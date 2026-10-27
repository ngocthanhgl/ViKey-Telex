/**
 * Parsers for universal-swipe `metadata.json` sidecar files.
 *
 * Every exported .pte from universal-swipe ships with a sibling metadata.json
 * that documents the I/O shapes
 *
 * The parser is simplified and not a full JSON parser, it does simple string
 * matching to get what it needs.
 */

#ifndef SWIPE_DECODER_MODEL_METADATA_HPP
#define SWIPE_DECODER_MODEL_METADATA_HPP

#include <string>

namespace swipe_decoder {

struct ScoringParams {
    float gamma = 0.1017f;  // length normalization exponent
    float lambda = 0.0373f; // frequency bonus weight
    float beta = 2.1745f;   // length bonus
    float alpha = 1.0f;     // language model weight
    float gamma_prune = 0.0f;
    float beta_prune  = 0.0f;
};

struct EncoderMetadata {
    std::string codename;
    std::string kind;     // "encoder"
    std::string layout;   // layout used during training (informational)
    int input_length = 0; // T from input_shapes.features (e.g. 64)
    int max_keys = 0;     // K_max from metadata (e.g. 64)
    int coeff_dim = 0;    // freq_x * freq_y (e.g. 64)
};

struct DecoderMetadata {
    std::string codename;
    std::string kind;     // "decoder"
    std::string layout;   // layout name this decoder is paired with (e.g. "en_qwerty")
    int input_length = 0; // T
    int input_dim = 0;    // (K+1) + coeff_dim + 1
    int num_chars = 0;    // K (layout alphabet size)
};

struct ContextLMMetadata {
    std::string codename;
    std::string kind;     // "contextlm"
};

/**
 * Load encoder metadata from the specified json file.
 *
 * @param metadata_path  Path to metadata.json (typically alongside model.pte).
 * @param[out] out       Populated on success.
 * @return true on success.
 */
bool load_encoder_metadata(const std::string& metadata_path, EncoderMetadata& out);

/**
 * Load decoder metadata from the specified json file.
 */
bool load_decoder_metadata(const std::string& metadata_path, DecoderMetadata& out);

/**
 * Load context_lm metadata from the specified json file.
 */
bool load_contextlm_metadata(const std::string& metadata_path, ContextLMMetadata& out);

/**
 * Return `<model_path without .pte> + .json`
 */
std::string metadata_path_for(const std::string& model_path);

}  // namespace swipe_decoder

#endif  // SWIPE_DECODER_MODEL_METADATA_HPP
