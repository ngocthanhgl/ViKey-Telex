/**
 * Unicode simple tolower - runtime-installed table.
 *
 * Currently this is only used by non-English ContextLM, and for the built-in
 * Trie implementation. It's not really used otherwise.
 */

#ifndef SWIPE_DECODER_UNICODE_CASE_HPP
#define SWIPE_DECODER_UNICODE_CASE_HPP

#include <cstddef>

namespace swipe_decoder {

/**
 * Install BMP tolower pairs. Arrays must be the same length and sorted
 * by `from` ascending; pass nullptr / 0 to clear
 */
void set_case_mapping(const int* from, const int* to, std::size_t n);

/** Binary-search the installed table; identity on miss. */
int case_lookup_lower(int cp);

/**
 * ASCII fast path + installed-table lookup
 */
inline int unicode_tolower(int cp) {
    if (cp >= 'A' && cp <= 'Z') return cp + 32;
    if (cp < 0x0080) return cp;
    return case_lookup_lower(cp);
}

}  // namespace swipe_decoder

#endif  // SWIPE_DECODER_UNICODE_CASE_HPP
