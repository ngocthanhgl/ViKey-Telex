/**
 * Trie-constrained CTC beam search decoder.
 */

#ifndef SWIPE_DECODER_BEAM_SEARCH_HPP
#define SWIPE_DECODER_BEAM_SEARCH_HPP

#include <cstdint>
#include <deque>
#include <limits>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "swipe_decoder/itrie.h"
#include "swipe_decoder/trie.hpp"
#include "swipe_decoder/model_metadata.hpp"

namespace swipe_decoder {

/**
 * Decoded word candidate with score breakdown.
 */
struct DecodedWord {
    std::string word;
    float ctc_score;       // Raw CTC log probability
    float freq_score;      // Frequency bonus contribution
    float word_length;     // Word length (character count)
    float final_score;     // Total score
    float frequency;       // Word frequency from vocabulary
    float lm_score = 0.0f; // Context LM contribution (before alpha scaling)
};

/**
 * Profiling data from an instrumented beam search decode.
 */
struct BeamSearchProfile {
    double total_ms = 0;
    double expansion_ms = 0;      // hypothesis expansion (blank + char emit + map ops)
    double pruning_ms = 0;        // partial_sort / sort
    double postproc_ms = 0;       // word filtering + scoring + reconstruction
    int total_hypotheses = 0;     // hypotheses expanded across all timesteps
    int peak_candidates = 0;      // max new_beam_vec.size() across timesteps
    size_t peak_memory_bytes = 0; // RSS after decode (Linux only)
};

/**
 * Parse a combined-vocab file into the shared ParsedCombined structure.
 * Returns false if the file can't be opened.
 */
bool parse_combined_vocab(const std::string& path, const std::string& freq_key,
                          ParsedCombined& out);

/**
 * Apply a parsed ParsedCombined to a trie
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


struct BeamHypothesis;
/**
 * Trie-constrained CTC beam search decoder.
 *
 * Decodes CTC log probabilities into word candidates,
 * constrained to only produce words in the vocabulary.
 */
class TrieBeamSearch {
public:
    /**
     * Create decoder with vocabulary trie.
     *
     * @param max_beam_width Maximum beam width that will be allowed
     * @param scoring Initial scoring parameters
     * @param num_chars Number of characters in alphabet (default: from trie)
     */
    explicit TrieBeamSearch(
        int max_beam_width,
        const ScoringParams &scoring,
        int num_chars = 0  // 0 = use trie's num_chars
    );

    ~TrieBeamSearch();

    /**
     * Decode two streams of CTC log probabilities to word candidates.
     * @return Vector of (word, score) pairs, sorted by score descending
     */
    std::vector<std::pair<std::string, float>> decode_multi(
        const ITrie *itrie,
        const float* left_logprobs,
        const float* left_times,
        size_t left_time_steps,
        const float* right_logprobs,
        const float* right_times,
        size_t right_time_steps,
        int top_k
    );

    /**
     * Decode two streams of CTC log probabilities iteratively with score breakdown.
     * Call finish_decode once finished.
     */
    void decode_with_scores_multi(
        const ITrie *itrie,
        std::vector<DecodedWord> &words_out,
        const float* left_logprobs,
        const float* left_times,
        size_t left_time_steps,
        const float* right_logprobs,
        const float* right_times,
        size_t right_time_steps,
        float weight = 1.0f,
        int beam_width = 100
    );

    /**
     * Decode CTC log probabilities to word candidates.
     *
     * @param itrie Trie dictionary to decode with
     * @param log_probs Log probabilities [T, 27] flattened row-major
     * @param time_steps Number of time steps T
     * @param top_k Maximum candidates to return
     * @return Vector of (word, score) pairs, sorted by score descending
     */
    inline std::vector<std::pair<std::string, float>> decode(
        const ITrie *itrie,
        const float* log_probs,
        int time_steps,
        int top_k
    ) {
        return decode_multi(itrie, log_probs, nullptr, time_steps, nullptr, nullptr, 0, top_k);
    }

    /**
     * Decode iteratively with score breakdown. Call finish_decode once finished.
     *
     * @param itrie Trie dictionary to decode with
     * @param words_out Vector to output results to
     * @param log_probs Log probabilities [T, 27] flattened row-major
     * @param time_steps Number of time steps T
     * @param itrie Trie (dictionary) to use for decoding
     * @param weight Weight to apply to these results (1.0 for typical)
     */
    inline void decode_with_scores(
        const ITrie *itrie,
        std::vector<DecodedWord> &words_out,
        const float* log_probs,
        int time_steps,
        float weight = 1.0f,
        int beam_width = 100
    ) {
        return decode_with_scores_multi(itrie, words_out, log_probs, nullptr, time_steps, nullptr, nullptr, 0, weight, beam_width);
    }

    /**
     * Finishes iterative decode (via decode_with_scores/decode_with_scores_multi).
     * Sorts words and limits them to maximum candidates.
     */
    void finish_decode(std::vector<DecodedWord> &words_out, int top_k = 3);

    // Setters
    inline void set_scoring(const ScoringParams &new_scoring) {
        freq_bonus_ = new_scoring.lambda;
        length_norm_exp_ = new_scoring.gamma;
        length_bonus_ = new_scoring.beta;
        gamma_prune_ = new_scoring.gamma_prune;
        beta_prune_  = new_scoring.beta_prune;
    }

    inline void set_single_mode(bool value) {
        single_mode_ = value;
    }

    // Accessors
    float freq_bonus() const { return freq_bonus_; }
    float length_norm_exp() const { return length_norm_exp_; }
    float length_bonus() const { return length_bonus_; }
    float gamma_prune() const { return gamma_prune_; }
    float beta_prune() const { return beta_prune_; }
    int num_chars() const { return num_chars_; }
    int num_classes() const { return num_classes_; }

private:
    bool single_mode_ = true;
    int max_beam_width_;
    float freq_bonus_;
    float length_norm_exp_;
    float length_bonus_;
    float gamma_prune_ = 0.0f;
    float beta_prune_  = 0.0f;
    int num_chars_;
    int num_classes_;
    int blank_idx_;

    struct Impl;
    std::unique_ptr<Impl> impl_;

    void advance_beam_search(
        const ITrie *trie,
        const BeamHypothesis &hyp,
        const float *probs_ts,
        float advance_lag, float advance_delta,
        uint16_t left_advance, uint16_t right_advance
    );

    void run_beam_search(
        const ITrie *trie,
        const float* left_logprobs,
        const float* left_times,
        size_t left_time_steps,
        const float* right_logprobs,
        const float* right_times,
        size_t right_time_steps,
        int beam_width
    );
};

}  // namespace swipe_decoder

#endif  // SWIPE_DECODER_BEAM_SEARCH_HPP
