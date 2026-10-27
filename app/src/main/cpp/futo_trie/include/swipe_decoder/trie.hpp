/**
 * Built-in trie implementation that works on importing .combined text files
 */

#ifndef SWIPE_DECODER_TRIE_HPP
#define SWIPE_DECODER_TRIE_HPP

#include <cstdint>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>
#include <limits>
#include <deque>
#include <optional>
#include "swipe_decoder/itrie.h"

namespace swipe_decoder {

// CTC constants - defaults for English a-z
constexpr int NUM_CHARS = 26;      // a-z
constexpr int BLANK_IDX = 26;      // CTC blank token
constexpr int NUM_CLASSES = 27;    // 26 chars + blank
constexpr int MAX_CHARS = 48;      // max supported alphabet (covers Latin, Cyrillic, etc.)

/**
 * One surface-form occurrence from the combined vocab file.
 *
 * `surface_ptr` points into ParsedCombined's owned backing storage - either
 * the raw file buffer (common case) or a side buffer for apostrophe-
 * normalized surfaces
 */
struct ParsedForm {
    const char* surface_ptr;   // into ParsedCombined::file_buf or norm_store
    uint32_t    surface_len;
    uint32_t    alpha_idx;     // index into ParsedCombined::alphas
    float       freq;
};

/**
 * Unique alpha-form group. Its `form_indices` index into ParsedCombined::forms.
 */
struct ParsedAlpha {
    std::string alpha;
    float       max_freq = -std::numeric_limits<float>::infinity();
    std::vector<uint32_t> form_indices;
};

/**
 * Parsed combined-vocab contents.
 *
 * `forms` are in file order, deduplicated by surface. `alphas` groups them
 * by alpha-form.
 */
class ParsedCombined {
public:
    std::vector<ParsedForm>  forms;
    std::vector<ParsedAlpha> alphas;

    size_t size() const { return alphas.size(); }
    bool   empty() const { return alphas.empty(); }

    // Backing storage - referenced by ParsedForm::surface_ptr. Public so
    // parse_combined_vocab can populate it; downstream consumers shouldn't
    // need to touch it.
    std::string file_buf;
    std::deque<std::string> norm_store;
};

/**
 * Compact trie node for vocabulary lookup.
 *
 * Uses array-based children for O(1) lookup instead of hash map.
 * Parent pointers enable word reconstruction without storing strings.
 */
struct TrieNode {
    // Child indices (0 = no child). Index into node array.
    uint32_t children[MAX_CHARS] = {0};

    // Word info
    bool is_word = false;
    float frequency = 0.0f;
    float log_frequency = -100.0f;

    // Parent chain for word reconstruction
    uint32_t parent = 0;     // parent node index (0 = root)
    uint8_t parent_char = 0; // char index on edge from parent (0-25)
    uint16_t depth = 0;      // depth = word length at this node
};


/**
 * Vocabulary trie for constrained beam search.
 *
 * Compact array-based trie with O(1) child lookup.
 * Optimized for cache-friendly traversal.
 */
class Trie {
public:
    /** Default constructor: English a-z alphabet. */
    Trie();

    /** Configurable alphabet constructor.
     * @param letters  Characters in the alphabet, e.g. "abcdefghijklmnopqrstuvwxyz"
     */
    explicit Trie(const std::string& letters);

    ~Trie();

    // Non-copyable, movable
    Trie(const Trie&) = delete;
    Trie& operator=(const Trie&) = delete;
    Trie(Trie&&) noexcept;
    Trie& operator=(Trie&&) noexcept;

    /** Number of characters in the alphabet. */
    int num_chars() const { return num_chars_; }

    /** The alphabet string (index -> character). */
    const std::string& letters() const { return letters_; }

    /**
     * Reserve capacity in the node array. Bulk-insertion callers should
     * call this before inserting many words to avoid repeated reallocations.
     */
    void reserve_nodes(size_t n);

    /**
     * Insert a word with frequency.
     *
     * @param word Word to insert (lowercase a-z only)
     * @param frequency Word frequency (higher = more common)
     * @param log_freq_direct If true, store frequency directly as log_frequency
     */
    void insert(const std::string& word, float frequency = 1.0f, bool log_freq_direct = false);

    /**
     * Check if word exists in vocabulary.
     */
    bool contains(const std::string& word) const;

    /**
     * Reconstruct word by walking the parent chain from a node.
     *
     * Only used for final top-k candidates, not during search.
     */
    std::string reconstruct_word(uint32_t node_idx) const;

    /**
     * Get number of words in vocabulary.
     */
    size_t size() const { return word_count_; }

    /**
     * Get root node for beam search traversal.
     */
    const TrieNode* root() const { return nodes_.empty() ? nullptr : &nodes_[0]; }

    /**
     * Get node by index (for beam search).
     */
    const TrieNode* get_node(uint32_t index) const {
        return index < nodes_.size() ? &nodes_[index] : nullptr;
    }

    /**
     * Look up a codepoint's character index. Returns -1 if not found.
     */
    int lookup_codepoint(int cp) const {
        auto it = cp_to_idx_.find(cp);
        return (it != cp_to_idx_.end()) ? it->second : -1;
    }

    ITrie *itrie();

private:
    ITrie itrie_;
    std::vector<TrieNode> nodes_;
    size_t word_count_ = 0;
    int num_chars_ = NUM_CHARS;
    std::string letters_ = "abcdefghijklmnopqrstuvwxyz";
    std::vector<int> code_points_;           // [num_chars_] codepoints
    std::unordered_map<int, int> cp_to_idx_; // codepoint -> char index

    void init_char_map();
};



/**
 * Parse a combined-vocab file into the shared ParsedCombined structure.
 * Returns false if the file can't be opened.
 */
bool parse_combined_vocab(const std::string& path, const std::string& freq_key,
                          ParsedCombined& out);

/**
 * Apply a parsed ParsedCombined to a trie.
 */
void apply_parsed_to_trie(const ParsedCombined& parsed, Trie& trie);

/**
 * Flatten a ParsedCombined into a list of (surface_form, freq) pairs for
 * the context LM's vocab table. The surface forms are unique (each surface
 * belongs to exactly one alpha, since alpha derivation is deterministic).
 *
 * Freq is clamped to uint32_t (combined-vocab `f=` values are integers).
 */
void flatten_parsed_to_entries(
    const ParsedCombined& parsed,
    std::vector<std::pair<std::string, uint32_t>>& out);

/**
 * Load Trie-ITrie pair from a vocab path. The Trie must not be freed for as
 * long as the itrie will be used.
 */
inline std::optional<Trie> load_trie_simple(const std::string &path) {
    ParsedCombined combined;
    Trie result;
    if(!parse_combined_vocab(path, "f", combined)) return std::nullopt;
    apply_parsed_to_trie(combined, result);
    return result;
}

}
#endif