/**
 * Keyboard layout description for runtime key lookup.
 *
 * A layout maps key indices (0..K-1) to characters and spatial positions.
 * Used by the DCT spatial head to evaluate the basis at key centers, and
 * by the trie/beam search for alphabet-aware decoding.
 *
 * Supports Unicode alphabets (Latin, Cyrillic, Greek, Indic, etc.) via
 * codepoint-based indexing.
 */

#ifndef SWIPE_DECODER_LAYOUT_HPP
#define SWIPE_DECODER_LAYOUT_HPP

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

// Unicode lowercase mapping backed by a runtime-installed table.
// On Android the table is injected from Kotlin at vocab-load time via
// JNI (nativeSetCaseMapping), so the platform's current Unicode version
// owns correctness.
#include "swipe_decoder/unicode_case.hpp"

namespace swipe_decoder {

// ---------------------------------------------------------------------------
// UTF-8 helpers
// ---------------------------------------------------------------------------

/**
 * Decode one UTF-8 codepoint from a byte sequence.
 *
 * @param s   Pointer to the start of the UTF-8 sequence
 * @param end Pointer past the end of the buffer
 * @param[out] cp  Decoded codepoint (set to 0xFFFD on error)
 * @return Pointer to the next byte after the decoded character
 */
inline const char* utf8_decode_one(const char* s, const char* end, int& cp) {
    if (s >= end) { cp = 0xFFFD; return end; }
    unsigned char c = static_cast<unsigned char>(*s);
    if (c < 0x80) {
        cp = c;
        return s + 1;
    } else if ((c & 0xE0) == 0xC0) {
        if (s + 2 > end) { cp = 0xFFFD; return end; }
        cp = (c & 0x1F) << 6
           | (static_cast<unsigned char>(s[1]) & 0x3F);
        return s + 2;
    } else if ((c & 0xF0) == 0xE0) {
        if (s + 3 > end) { cp = 0xFFFD; return end; }
        cp = (c & 0x0F) << 12
           | (static_cast<unsigned char>(s[1]) & 0x3F) << 6
           | (static_cast<unsigned char>(s[2]) & 0x3F);
        return s + 3;
    } else if ((c & 0xF8) == 0xF0) {
        if (s + 4 > end) { cp = 0xFFFD; return end; }
        cp = (c & 0x07) << 18
           | (static_cast<unsigned char>(s[1]) & 0x3F) << 12
           | (static_cast<unsigned char>(s[2]) & 0x3F) << 6
           | (static_cast<unsigned char>(s[3]) & 0x3F);
        return s + 4;
    }
    cp = 0xFFFD;
    return s + 1;
}

/**
 * Decode all codepoints from a UTF-8 string.
 */
inline std::vector<int> utf8_to_codepoints(const std::string& s) {
    std::vector<int> cps;
    const char* p = s.data();
    const char* end = p + s.size();
    while (p < end) {
        int cp;
        p = utf8_decode_one(p, end, cp);
        if (cp != 0xFFFD) cps.push_back(cp);
    }
    return cps;
}

/**
 * Encode a single codepoint as UTF-8 and append to a string.
 */
inline void utf8_encode_one(int cp, std::string& out) {
    if (cp < 0x80) {
        out += static_cast<char>(cp);
    } else if (cp < 0x800) {
        out += static_cast<char>(0xC0 | (cp >> 6));
        out += static_cast<char>(0x80 | (cp & 0x3F));
    } else if (cp < 0x10000) {
        out += static_cast<char>(0xE0 | (cp >> 12));
        out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
        out += static_cast<char>(0x80 | (cp & 0x3F));
    } else {
        out += static_cast<char>(0xF0 | (cp >> 18));
        out += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
        out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
        out += static_cast<char>(0x80 | (cp & 0x3F));
    }
}

inline int simple_tolower(int cp) { return unicode_tolower(cp); }

/**
 * Simple uppercase mapping (inverse of simple_tolower).
 */
inline int simple_toupper(int cp) {
    if (cp >= 'a' && cp <= 'z') return cp - 32;
    if (cp >= 0x0430 && cp <= 0x044F) return cp - 0x20;
    if (cp == 0x0451) return 0x0401;
    return cp;
}

/** Lowercase an entire UTF-8 string (codepoint-level). */
inline std::string utf8_tolower(const std::string& s) {
    auto cps = utf8_to_codepoints(s);
    std::string result;
    result.reserve(s.size());
    for (int cp : cps) {
        utf8_encode_one(unicode_tolower(cp), result);
    }
    return result;
}

/**
 * Extract alpha-only form from a word (lowercased, non-letter chars stripped).
 * Supports Latin a-z, Cyrillic, and other Unicode letter codepoints.
 */
inline std::string to_alpha_form(const std::string& word) {
    auto cps = utf8_to_codepoints(word);
    std::string alpha;
    alpha.reserve(word.size());
    for (int cp : cps) {
        int lower = simple_tolower(cp);
        // Accept: ASCII a-z, Cyrillic а-я (0x430-0x44F), ё (0x451),
        // and any non-ASCII letter (heuristic: codepoint > 127 that survived tolower)
        bool is_alpha = (lower >= 'a' && lower <= 'z')
                     || (lower >= 0x0430 && lower <= 0x044F)
                     || lower == 0x0451
                     || (lower > 0x7F && lower < 0xFFFD);
        if (is_alpha) {
            utf8_encode_one(lower, alpha);
        }
    }
    return alpha;
}

// ---------------------------------------------------------------------------
// KeyboardLayout
// ---------------------------------------------------------------------------

struct KeyboardLayout {
    int num_keys = 0;                    // K (26 for en, 33 for ru, etc.)
    std::string letters;                 // UTF-8: index -> character string
    std::vector<int> code_points;        // [K] Unicode codepoints
    std::vector<float> key_cx;           // [K] center x in [0, 1]
    std::vector<float> key_cy;           // [K] center y in [0, 1]

    // Codepoint -> key index mapping (supports full Unicode)
    std::unordered_map<int, int> codepoint_to_idx;

    /**
     * Build the codepoint_to_idx map from letters/code_points.
     * If code_points is empty, decodes from the UTF-8 letters string.
     * Layout letters are expected to be the lowercase alpha-form; callers
     * pre-lower lookup codepoints via simple_tolower.
     */
    void build_index() {
        if (code_points.empty() && !letters.empty()) {
            code_points = utf8_to_codepoints(letters);
        }
        codepoint_to_idx.clear();
        for (int i = 0; i < static_cast<int>(code_points.size()); ++i) {
            codepoint_to_idx[code_points[i]] = i;
        }
    }

    /**
     * Look up a codepoint's key index. Returns -1 if not found.
     */
    int lookup(int codepoint) const {
        auto it = codepoint_to_idx.find(codepoint);
        return (it != codepoint_to_idx.end()) ? it->second : -1;
    }
};

/**
 * Load a keyboard layout from a JSON file.
 *
 * Expected format:
 * {
 *   "letters": "abcdefghijklmnopqrstuvwxyz",
 *   "keys": [
 *     {"letter": "a", "cx": 0.10, "cy": 0.50},
 *     ...
 *   ]
 * }
 *
 * The "letter" field in each key object may be a multi-byte UTF-8 character.
 * Uses simplified JSON parsing, doesn't support full JSON
 */
inline bool load_layout_json(const std::string& path, KeyboardLayout& layout) {
    std::ifstream f(path);
    if (!f.is_open()) {
        fprintf(stderr, "layout: cannot open '%s'\n", path.c_str());
        return false;
    }

    std::string content((std::istreambuf_iterator<char>(f)),
                         std::istreambuf_iterator<char>());

    // Extract "letters": "..."
    auto extract_string = [&](const std::string& key) -> std::string {
        std::string needle = "\"" + key + "\"";
        size_t pos = content.find(needle);
        if (pos == std::string::npos) return "";
        pos = content.find('"', pos + needle.size());
        if (pos == std::string::npos) return "";
        pos++;  // skip opening quote
        size_t end = content.find('"', pos);
        if (end == std::string::npos) return "";
        return content.substr(pos, end - pos);
    };

    layout.letters = extract_string("letters");
    if (layout.letters.empty()) {
        fprintf(stderr, "layout: no 'letters' field in '%s'\n", path.c_str());
        return false;
    }

    layout.code_points = utf8_to_codepoints(layout.letters);
    layout.num_keys = static_cast<int>(layout.code_points.size());
    layout.key_cx.resize(layout.num_keys, 0.0f);
    layout.key_cy.resize(layout.num_keys, 0.0f);

    // Parse keys array - find each {"letter": "X", "cx": F, "cy": F}
    size_t keys_pos = content.find("\"keys\"");
    if (keys_pos == std::string::npos) {
        fprintf(stderr, "layout: no 'keys' array in '%s'\n", path.c_str());
        return false;
    }

    // Find the opening bracket of the keys array
    size_t arr_start = content.find('[', keys_pos);
    if (arr_start == std::string::npos) return false;

    size_t pos = arr_start;
    int keys_found = 0;
    while (pos < content.size()) {
        // Find next object
        size_t obj_start = content.find('{', pos);
        if (obj_start == std::string::npos) break;

        size_t obj_end = content.find('}', obj_start);
        if (obj_end == std::string::npos) break;

        std::string obj = content.substr(obj_start, obj_end - obj_start + 1);

        // Extract letter (may be multi-byte UTF-8)
        size_t lpos = obj.find("\"letter\"");
        if (lpos == std::string::npos) { pos = obj_end + 1; continue; }
        size_t q1 = obj.find('"', lpos + 8);
        size_t q2 = obj.find('"', q1 + 1);
        if (q1 == std::string::npos || q2 == std::string::npos) { pos = obj_end + 1; continue; }
        std::string letter_str = obj.substr(q1 + 1, q2 - q1 - 1);

        // Extract cx
        auto extract_float = [&](const std::string& key) -> float {
            std::string needle = "\"" + key + "\"";
            size_t p = obj.find(needle);
            if (p == std::string::npos) return 0.0f;
            p = obj.find(':', p + needle.size());
            if (p == std::string::npos) return 0.0f;
            p++;
            while (p < obj.size() && (obj[p] == ' ' || obj[p] == '\t')) p++;
            try { return std::stof(obj.substr(p)); } catch (...) { return 0.0f; }
        };

        float cx = extract_float("cx");
        float cy = extract_float("cy");

        // Find this letter's index via codepoint lookup
        if (!letter_str.empty()) {
            auto letter_cps = utf8_to_codepoints(letter_str);
            if (!letter_cps.empty()) {
                int cp = letter_cps[0];
                // Find in code_points array
                for (int i = 0; i < layout.num_keys; ++i) {
                    if (layout.code_points[i] == cp) {
                        layout.key_cx[i] = cx;
                        layout.key_cy[i] = cy;
                        keys_found++;
                        break;
                    }
                }
            }
        }

        pos = obj_end + 1;
    }

    if (keys_found != layout.num_keys) {
        fprintf(stderr, "layout: found %d/%d keys in '%s'\n",
                keys_found, layout.num_keys, path.c_str());
    }

    layout.build_index();
    return keys_found > 0;
}

/**
 * Build a KeyboardLayout from raw arrays (e.g. from JNI).
 *
 * @param letters_utf8  UTF-8 encoded letters string (alphabetically sorted)
 * @param cx            Normalized center-x positions [num_keys]
 * @param cy            Normalized center-y positions [num_keys]
 * @param num_keys      Number of keys
 */
inline KeyboardLayout make_layout(const std::string& letters_utf8,
                                  const float* cx, const float* cy,
                                  int num_keys) {
    KeyboardLayout layout;
    layout.letters = letters_utf8;
    layout.code_points = utf8_to_codepoints(letters_utf8);
    layout.num_keys = num_keys;
    layout.key_cx.assign(cx, cx + num_keys);
    layout.key_cy.assign(cy, cy + num_keys);
    layout.build_index();
    return layout;
}

/**
 * Built-in English QWERTY layout (26 keys, 3-row staggered).
 *
 * Coordinates from universal-swipe en_qwerty layout (normalized 0-1).
 */
inline KeyboardLayout default_en_qwerty() {
    KeyboardLayout layout;
    layout.letters = "abcdefghijklmnopqrstuvwxyz";
    layout.num_keys = 26;

    // clang-format off
    //                   a      b      c      d      e      f      g      h      i      j      k      l      m
    layout.key_cx = { 0.100f,0.600f,0.400f,0.300f,0.250f,0.400f,0.500f,0.600f,0.750f,0.700f,0.800f,0.900f,0.800f,
    //                   n      o      p      q      r      s      t      u      v      w      x      y      z
                      0.700f,0.850f,0.950f,0.050f,0.350f,0.200f,0.450f,0.650f,0.500f,0.150f,0.300f,0.550f,0.200f };

    //                   a      b      c      d      e      f      g      h      i      j      k      l      m
    layout.key_cy = { 0.500f,0.833f,0.833f,0.500f,0.167f,0.500f,0.500f,0.500f,0.167f,0.500f,0.500f,0.500f,0.833f,
    //                   n      o      p      q      r      s      t      u      v      w      x      y      z
                      0.833f,0.167f,0.167f,0.167f,0.167f,0.500f,0.167f,0.167f,0.833f,0.167f,0.833f,0.167f,0.833f };
    // clang-format on

    layout.code_points = utf8_to_codepoints(layout.letters);
    layout.build_index();
    return layout;
}

}  // namespace swipe_decoder

#endif  // SWIPE_DECODER_LAYOUT_HPP
