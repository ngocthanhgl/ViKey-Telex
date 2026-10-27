/**
 * Vocabulary parsing and hashing for context LM.
 *
 * Computes the hash table at init from vocab.combined, eliminating the
 * need for a separate hash_table.bin file.
 *
 * Uses wyhash for string hashing and multiply-shift for bucket assignment.
 */

#ifndef SWIPE_DECODER_VOCAB_HASH_HPP
#define SWIPE_DECODER_VOCAB_HASH_HPP

#include <cstdint>
#include <cstring>
#include <deque>
#include <string>
#include <vector>

namespace swipe_decoder {

// Portable 64x64->128 multiply
inline uint64_t _wymix_portable(uint64_t a, uint64_t b) {
    uint64_t ha = a >> 32, la = static_cast<uint32_t>(a);
    uint64_t hb = b >> 32, lb = static_cast<uint32_t>(b);
    uint64_t rh = ha * hb, rl = la * lb;
    uint64_t rm0 = ha * lb, rm1 = hb * la;
    uint64_t t = rl + (rm0 << 32);
    uint64_t lo = t + (rm1 << 32);
    uint64_t hi = rh + (rm0 >> 32) + (rm1 >> 32)
                + (t < rl) + (lo < t);
    return lo ^ hi;
}

inline uint64_t _wymix(uint64_t a, uint64_t b) {
#if defined(__SIZEOF_INT128__)
    __uint128_t r = static_cast<__uint128_t>(a) * b;
    return static_cast<uint64_t>(r) ^ static_cast<uint64_t>(r >> 64);
#else
    return _wymix_portable(a, b);
#endif
}

inline uint64_t _wyr8(const uint8_t* p) {
    uint64_t v; memcpy(&v, p, 8); return v;
}
inline uint64_t _wyr4(const uint8_t* p) {
    uint32_t v; memcpy(&v, p, 4); return v;
}
inline uint64_t _wyr3(const uint8_t* p, size_t k) {
    return (static_cast<uint64_t>(p[0]) << 16) |
           (static_cast<uint64_t>(p[k >> 1]) << 8) |
           static_cast<uint64_t>(p[k - 1]);
}

inline uint64_t wyhash(const void* key, size_t len, uint64_t seed = 0) {
    static constexpr uint64_t s0 = 0xa0761d6478bd642fULL;
    static constexpr uint64_t s1 = 0xe7037ed1a0b428dbULL;
    static constexpr uint64_t s2 = 0x8ebc6af09c88c6e3ULL;
    static constexpr uint64_t s3 = 0x589965cc75374cc3ULL;

    const auto* p = static_cast<const uint8_t*>(key);
    seed ^= _wymix(seed ^ s0, s1);
    uint64_t a, b;

    if (len <= 16) {
        if (len >= 4) {
            a = (_wyr4(p) << 32) | _wyr4(p + ((len >> 3) << 2));
            b = (_wyr4(p + len - 4) << 32) | _wyr4(p + len - 4 - ((len >> 3) << 2));
        } else if (len > 0) {
            a = _wyr3(p, len);
            b = 0;
        } else {
            a = b = 0;
        }
    } else if (len <= 48) {
        size_t i = 0;
        a = 0; b = 0;
        for (; i + 16 <= len; i += 16)
            seed = _wymix(_wyr8(p + i) ^ s1, _wyr8(p + i + 8) ^ seed);
        a = _wyr8(p + len - 16);
        b = _wyr8(p + len - 8);
    } else {
        size_t i = 0;
        uint64_t see1 = seed, see2 = seed;
        for (; i + 48 <= len; i += 48) {
            seed = _wymix(_wyr8(p + i) ^ s1, _wyr8(p + i + 8) ^ seed);
            see1 = _wymix(_wyr8(p + i + 16) ^ s2, _wyr8(p + i + 24) ^ see1);
            see2 = _wymix(_wyr8(p + i + 32) ^ s3, _wyr8(p + i + 40) ^ see2);
        }
        for (; i + 16 <= len; i += 16)
            seed = _wymix(_wyr8(p + i) ^ s1, _wyr8(p + i + 8) ^ seed);
        seed ^= see1 ^ see2;
        a = _wyr8(p + len - 16);
        b = _wyr8(p + len - 8);
    }
    return _wymix(s1 ^ len, _wymix(a ^ s1, b ^ seed));
}

// Multiply-shift bucket assignment: bucket = (a * key) >> (64 - log2(num_buckets))
// Constants are odd 64-bit numbers for good distribution.
static constexpr uint64_t MULSHIFT_A0 = 0x9E3779B97F4A7C15ULL;  // golden ratio
static constexpr uint64_t MULSHIFT_A1 = 0x517CC1B727220A95ULL;

inline uint32_t multiply_shift(uint64_t key, uint64_t a, int shift) {
    return static_cast<uint32_t>((a * key) >> shift);
}

/**
 * Compute 2 hash bucket indices for a word using wyhash + multiply-shift.
 * num_buckets must be a power of 2.
 */
inline void compute_hash_indices_wy(
    const char* word, size_t len,
    int num_buckets,
    uint32_t out[2]
) {
    int shift = 64 - __builtin_ctz(num_buckets);
    uint64_t key = wyhash(word, len);
    out[0] = multiply_shift(key, MULSHIFT_A0, shift);
    out[1] = multiply_shift(key, MULSHIFT_A1, shift);
}

}  // namespace swipe_decoder

#endif  // SWIPE_DECODER_VOCAB_HASH_HPP
