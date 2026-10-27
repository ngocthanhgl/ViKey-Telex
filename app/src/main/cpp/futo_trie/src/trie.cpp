#include "swipe_decoder/trie.hpp"
#include "swipe_decoder/layout.hpp"
#include "swipe_decoder/itrie.h"
#include "swipe_decoder/beam_search.hpp"
#include "swipe_decoder/vocab_hash.hpp"  // wyhash

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <fstream>
#include <sstream>
#include <unordered_map>

#ifdef __linux__
#include <cstdio>
#endif


namespace swipe_decoder {

// Open-addressing surface-dedup slot. Indexes into ParsedCombined::forms.
namespace {
struct SurfaceSlot {
    uint32_t form_idx;   // UINT32_MAX = empty
    uint32_t hash_val;
};
}  // namespace

// ============================================================================
// Combined vocab parsing helpers
// ============================================================================

// Read entire file into memory. Returns empty string on failure.
static std::string read_file_bytes(const std::string& path) {
    std::ifstream f(path, std::ios::ate | std::ios::binary);
    if (!f) return {};
    size_t sz = static_cast<size_t>(f.tellg());
    f.seekg(0);
    std::string buf(sz, '\0');
    f.read(buf.data(), static_cast<std::streamsize>(sz));
    return buf;
}

// Fast check: does this byte range contain any apostrophe variant we normalize?
// Returning false lets the caller skip the per-byte normalization pass.
static inline bool needs_apostrophe_norm(const char* s, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        unsigned char c = static_cast<unsigned char>(s[i]);
        if (c == 0xE2 || c == 0xC2 || c == '`') return true;
    }
    return false;
}

// Byte-wise copy with apostrophe-variant bytes replaced by ASCII '. Only call
// when needs_apostrophe_norm() is true.
static std::string normalize_apostrophes(const char* s, size_t len) {
    std::string out;
    out.reserve(len);
    for (size_t i = 0; i < len;) {
        unsigned char c = static_cast<unsigned char>(s[i]);
        if (c == 0xE2 && i + 2 < len) {
            unsigned char c1 = static_cast<unsigned char>(s[i + 1]);
            unsigned char c2 = static_cast<unsigned char>(s[i + 2]);
            // U+2018/U+2019 curly apostrophes: E2 80 98 / E2 80 99
            if (c1 == 0x80 && (c2 == 0x99 || c2 == 0x98)) {
                out += '\''; i += 3; continue;
            }
        } else if (c == 0xC2 && i + 1 < len
                   && static_cast<unsigned char>(s[i + 1]) == 0xB4) {
            // U+00B4 acute accent: C2 B4
            out += '\''; i += 2; continue;
        } else if (c == '`') {
            out += '\''; i += 1; continue;
        }
        out += static_cast<char>(c);
        i += 1;
    }
    return out;
}

// True when the range is pure ASCII (no byte has high bit set). Governs the
// fast ASCII path for alpha extraction.
static inline bool is_ascii(const char* s, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        if (static_cast<unsigned char>(s[i]) >= 0x80) return false;
    }
    return true;
}

// ASCII fast path: emit lowercased a-z only. Skips anything else.
static std::string ascii_alpha_form(const char* s, size_t len) {
    std::string alpha;
    alpha.reserve(len);
    for (size_t i = 0; i < len; ++i) {
        char c = s[i];
        char lower = c | 0x20;
        if (lower >= 'a' && lower <= 'z') alpha += lower;
    }
    return alpha;
}

// Unicode fallback: lowercases + keeps letter codepoints (Latin, Cyrillic,
// and other scripts we support). Only called when the surface has any byte
// with the high bit set.
static std::string unicode_alpha_form(const char* s, size_t len) {
    std::string alpha;
    alpha.reserve(len);
    const char* p = s;
    const char* end = s + len;
    while (p < end) {
        int cp;
        p = utf8_decode_one(p, end, cp);
        int lower = simple_tolower(cp);
        bool is_letter = (lower >= 'a' && lower <= 'z')
                      || (lower >= 0x0430 && lower <= 0x044F)
                      || lower == 0x0451
                      || (lower > 0x7F && lower < 0xFFFD);
        if (is_letter) utf8_encode_one(lower, alpha);
    }
    return alpha;
}

// Parse an unsigned integer. *pp is advanced past the digits; returns -1.0f
// if no digits consumed.
static inline float parse_u32_freq(const char*& pp, const char* end) {
    uint32_t v = 0;
    const char* start = pp;
    while (pp < end && *pp >= '0' && *pp <= '9') {
        v = v * 10 + static_cast<uint32_t>(*pp - '0');
        ++pp;
    }
    if (pp == start) return -1.0f;
    return static_cast<float>(v);
}


bool parse_combined_vocab(const std::string& path, const std::string& freq_key,
                          ParsedCombined& out) {
    out.forms.clear();
    out.alphas.clear();
    out.norm_store.clear();
    out.file_buf = read_file_bytes(path);
    if (out.file_buf.empty()) return false;

    const char* data = out.file_buf.data();
    const char* end  = data + out.file_buf.size();

    // Build the needle once (e.g. ",f=" or ",logFreq=").
    std::string needle = "," + freq_key + "=";
    const char* needle_data = needle.data();
    const size_t needle_len = needle.size();

    // Surface-dedup table. Sized to ~2x expected unique surfaces (~160k).
    size_t dedup_cap = 1;
    while (dedup_cap < 400000) dedup_cap <<= 1;
    const uint32_t dedup_mask = static_cast<uint32_t>(dedup_cap - 1);
    std::vector<SurfaceSlot> dedup(dedup_cap, {UINT32_MAX, 0});

    // Alpha-form hash table keyed on std::string (alpha-form strings are
    // usually freshly allocated). Reserved for ~144k unique alphas.
    std::unordered_map<std::string, uint32_t> alpha_idx_by_str;
    alpha_idx_by_str.reserve(200000);

    out.forms.reserve(200000);
    out.alphas.reserve(160000);

    const char* p = data;
    while (p < end) {
        const char* eol = static_cast<const char*>(memchr(p, '\n', end - p));
        if (!eol) eol = end;

        // Line starts with optional whitespace, then "word=".
        const char* lp = p;
        while (lp < eol && (*lp == ' ' || *lp == '\t')) ++lp;

        // Require "word=" prefix. Anything else (dictionary=, lang=, blank
        // lines, trailing keys) is silently skipped.
        if (eol - lp >= 5 && lp[0] == 'w' && lp[1] == 'o' &&
            lp[2] == 'r' && lp[3] == 'd' && lp[4] == '=') {

            const char* word_start = lp + 5;
            const char* comma = static_cast<const char*>(
                memchr(word_start, ',', eol - word_start));
            if (!comma) { p = eol + 1; continue; }

            // Find ",<freq_key>=" within the remainder of the line.
            const char* needle_hit = nullptr;
            if (static_cast<size_t>(eol - comma) >= needle_len) {
                // One-pass search starting at the first comma.
                const char* scan = comma;
                const char* scan_end = eol - (needle_len - 1);
                while (scan < scan_end) {
                    if (*scan == ',' && memcmp(scan, needle_data, needle_len) == 0) {
                        needle_hit = scan;
                        break;
                    }
                    ++scan;
                }
            }
            if (!needle_hit) { p = eol + 1; continue; }

            const char* fp = needle_hit + needle_len;
            float freq = parse_u32_freq(fp, eol);
            if (freq < 0) { p = eol + 1; continue; }

            const char* wptr = word_start;
            uint32_t wlen = static_cast<uint32_t>(comma - word_start);
            if (wlen == 0) { p = eol + 1; continue; }

            // Normalize apostrophes only when the surface has a byte that
            // can trigger a replacement. Most surfaces don't, so we keep
            // pointers into file_buf.
            if (needs_apostrophe_norm(wptr, wlen)) {
                out.norm_store.push_back(normalize_apostrophes(wptr, wlen));
                const std::string& n = out.norm_store.back();
                wptr = n.data();
                wlen = static_cast<uint32_t>(n.size());
            }

            // Surface dedup.
            uint32_t h = static_cast<uint32_t>(wyhash(wptr, wlen, 0x12345));
            uint32_t pos = h & dedup_mask;
            for (;;) {
                auto& slot = dedup[pos];
                if (slot.form_idx == UINT32_MAX) {
                    // New surface - compute alpha and register.
                    std::string alpha = is_ascii(wptr, wlen)
                        ? ascii_alpha_form(wptr, wlen)
                        : unicode_alpha_form(wptr, wlen);
                    if (alpha.empty()) break;  // no letter chars -> skip

                    auto [ait, a_inserted] =
                        alpha_idx_by_str.try_emplace(std::move(alpha),
                                                     static_cast<uint32_t>(out.alphas.size()));
                    if (a_inserted) {
                        out.alphas.push_back({ait->first, freq, {}});
                    } else {
                        if (freq > out.alphas[ait->second].max_freq) {
                            out.alphas[ait->second].max_freq = freq;
                        }
                    }
                    uint32_t form_idx = static_cast<uint32_t>(out.forms.size());
                    out.forms.push_back({wptr, wlen, ait->second, freq});
                    out.alphas[ait->second].form_indices.push_back(form_idx);

                    slot.form_idx = form_idx;
                    slot.hash_val = h;
                    break;
                }
                if (slot.hash_val == h) {
                    ParsedForm& existing = out.forms[slot.form_idx];
                    if (existing.surface_len == wlen &&
                        memcmp(existing.surface_ptr, wptr, wlen) == 0) {
                        // Duplicate - keep file_order (implicit form_idx)
                        // and bump freq if higher.
                        if (freq > existing.freq) {
                            existing.freq = freq;
                            if (freq > out.alphas[existing.alpha_idx].max_freq) {
                                out.alphas[existing.alpha_idx].max_freq = freq;
                            }
                        }
                        break;
                    }
                }
                pos = (pos + 1) & dedup_mask;
            }
        }

        p = eol + 1;
    }

    return true;
}

void apply_parsed_to_trie(const ParsedCombined& parsed, Trie& trie) {
    // Reserve a generous node count up front to avoid repeated vector
    // reallocations (each copies every TrieNode, ~220B each). Empirically
    // ~4x the alpha count covers most vocabularies with <10% waste.
    trie.reserve_nodes(parsed.alphas.size() * 4 + 16);
    for (const auto& a : parsed.alphas) {
        trie.insert(a.alpha, a.max_freq, true);
    }
}

void flatten_parsed_to_entries(
    const ParsedCombined& parsed,
    std::vector<std::pair<std::string, uint32_t>>& out) {
    // forms are already in file order
    out.clear();
    out.reserve(parsed.forms.size());
    for (const auto& f : parsed.forms) {
        uint32_t freq_u = (f.freq <= 0.0f) ? 0u : static_cast<uint32_t>(f.freq);
        out.emplace_back(std::string(f.surface_ptr, f.surface_len), freq_u);
    }
}

// ============================================================================
// Trie implementation
// ============================================================================

void Trie::init_char_map() {
    code_points_ = utf8_to_codepoints(letters_);
    num_chars_ = static_cast<int>(code_points_.size());
    cp_to_idx_.clear();
    for (int i = 0; i < num_chars_; ++i) {
        int cp = code_points_[i];
        cp_to_idx_[cp] = i;
        int upper = simple_toupper(cp);
        if (upper != cp) cp_to_idx_[upper] = i;
        int lower = simple_tolower(cp);
        if (lower != cp) cp_to_idx_[lower] = i;
    }
}

Trie::Trie() {
    init_char_map();
    // Pre-allocate root node
    nodes_.emplace_back();
}

Trie::Trie(const std::string& letters)
    : letters_(letters)
{
    init_char_map();  // sets num_chars_ from codepoint count
    nodes_.emplace_back();
}

Trie::~Trie() = default;

Trie::Trie(Trie&&) noexcept = default;
Trie& Trie::operator=(Trie&&) noexcept = default;

void Trie::reserve_nodes(size_t n) {
    nodes_.reserve(n);
}

void Trie::insert(const std::string& word, float frequency, bool log_freq_direct) {
    if (word.empty()) {
        return;
    }

    auto cps = utf8_to_codepoints(word);
    uint32_t node_idx = 0;  // Start at root

    for (size_t i = 0; i < cps.size(); ++i) {
        int cp = simple_tolower(cps[i]);
        int char_idx = lookup_codepoint(cp);
        if (char_idx < 0) {
            return;  // Invalid character, skip word
        }

        // Get or create child
        if (nodes_[node_idx].children[char_idx] == 0) {
            // Create new node
            uint32_t new_idx = static_cast<uint32_t>(nodes_.size());
            nodes_.emplace_back();
            // Set parent chain info on the new node
            nodes_[new_idx].parent = node_idx;
            nodes_[new_idx].parent_char = static_cast<uint8_t>(char_idx);
            nodes_[new_idx].depth = static_cast<uint16_t>(i + 1);
            nodes_[node_idx].children[char_idx] = new_idx;
        }

        node_idx = nodes_[node_idx].children[char_idx];
    }

    // Mark as word
    if (!nodes_[node_idx].is_word) {
        word_count_++;
    }
    nodes_[node_idx].is_word = true;
    nodes_[node_idx].frequency = frequency;
    nodes_[node_idx].log_frequency = log_freq_direct ? frequency : std::log(frequency + 1e-10f);
}

bool Trie::contains(const std::string& word) const {
    auto cps = utf8_to_codepoints(word);
    uint32_t node_idx = 0;

    for (int cp : cps) {
        int char_idx = lookup_codepoint(simple_tolower(cp));
        if (char_idx < 0) {
            return false;
        }

        uint32_t child_idx = nodes_[node_idx].children[char_idx];
        if (child_idx == 0) {
            return false;
        }
        node_idx = child_idx;
    }

    return nodes_[node_idx].is_word;
}

std::string Trie::reconstruct_word(uint32_t node_idx) const {
    if (node_idx == 0 || node_idx >= nodes_.size()) {
        return "";
    }

    const auto& node = nodes_[node_idx];

    // Collect char indices by walking parent chain
    std::vector<int> char_indices(node.depth);
    uint32_t idx = node_idx;
    for (int i = static_cast<int>(node.depth) - 1; i >= 0; --i) {
        char_indices[i] = static_cast<int>(nodes_[idx].parent_char);
        idx = nodes_[idx].parent;
    }

    // Convert char indices to UTF-8 string via codepoints
    std::string word;
    word.reserve(node.depth * 2);  // reasonable pre-alloc for multi-byte
    for (int ci : char_indices) {
        if (ci < static_cast<int>(code_points_.size())) {
            utf8_encode_one(code_points_[ci], word);
        } else {
            word += '?';
        }
    }

    return word;
}

// ITrie wrapper
static int Trie_NumChars(void* self) {
    return ((Trie *)self)->num_chars();
}
static TrieId Trie_Root(void* self) {
    return 0;
}
static int Trie_GetCodepoint(void* self, TrieId id) {
    return ((Trie *)self)->get_node(id)->parent_char;
}
static uint32_t Trie_GetChildCount(void* self, TrieId id) {
    const TrieNode *node = ((Trie *)self)->get_node(id);

    int count = 0;
    for(int i=0; i<MAX_CHARS; i++) {
        if(node->children[i] != 0) count++;
    }

    return count;
}
static TrieId Trie_GetChild(void* self, TrieId id, uint32_t idx) {
    const TrieNode *node = ((Trie *)self)->get_node(id);

    int count = 0;
    for(int i=0; i<MAX_CHARS; i++) {
        if(node->children[i] != 0) {
            if(count == idx) return node->children[i];
            count++;
        }
    }

    return 0;
}
static bool Trie_IsWord(void* self, TrieId id) {
    const TrieNode *node = ((Trie *)self)->get_node(id);
    return node->is_word;
}
static float Trie_GetLogFrequency(void* self, TrieId id) {
    const TrieNode *node = ((Trie *)self)->get_node(id);
    return node->log_frequency;
}
static uint16_t Trie_GetDepth(void* self, TrieId id) {
    const TrieNode *node = ((Trie *)self)->get_node(id);
    return node->depth;   
}
static const char *Trie_GetWord(void* self, TrieId id) {
    static char tmpbuff[512];

    std::string word = ((Trie *)self)->reconstruct_word(id);
    if(word.length() > 500) return NULL;
    strcpy(tmpbuff, word.c_str());
    return tmpbuff;
}
static void Trie_EndSearch(void *self) {
    // nothing
}

const static struct ITrieVTable TRIE_VTABLE = {
    &Trie_NumChars,
    &Trie_Root,
    
    &Trie_GetCodepoint, &Trie_GetChildCount,
    &Trie_GetChild, &Trie_IsWord,
    &Trie_GetLogFrequency, &Trie_GetDepth,
    &Trie_GetWord, &Trie_EndSearch
};

void populate_itrie(Trie *trie, ITrie *itrie) {
    itrie->userdata = (void*)trie;
    itrie->vtable = &TRIE_VTABLE;
}

ITrie *Trie::itrie() {
    populate_itrie(this, &itrie_);
    return &itrie_;
}

}