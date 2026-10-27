#include "swipe_decoder/unicode_case.hpp"

#include <algorithm>
#include <vector>

namespace swipe_decoder {
namespace {

// Construct-on-first-use - callers (including static-init fixtures in the
// test suite) can install a mapping before this TU's dynamic initialization
// runs without tripping the static-init-order fiasco.
std::vector<int>& from_vec() { static std::vector<int> v; return v; }
std::vector<int>& to_vec()   { static std::vector<int> v; return v; }

}  // namespace

void set_case_mapping(const int* from, const int* to, std::size_t n) {
    auto& f = from_vec();
    auto& t = to_vec();
    if (from == nullptr || to == nullptr || n == 0) {
        f.clear();
        t.clear();
        return;
    }
    f.assign(from, from + n);
    t.assign(to, to + n);
}

int case_lookup_lower(int cp) {
    const auto& f = from_vec();
    if (f.empty()) return cp;
    auto it = std::lower_bound(f.begin(), f.end(), cp);
    if (it == f.end() || *it != cp) return cp;
    return to_vec()[static_cast<std::size_t>(it - f.begin())];
}

}  // namespace swipe_decoder
