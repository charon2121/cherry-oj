#include <cstdint>
#include <iostream>
#include <vector>

int main() {
    int n, q;
    std::cin >> n >> q;
    std::vector<std::uint32_t> prefix(n + 1);
    for (int i = 1; i <= n; ++i) {
        std::int64_t value;
        std::cin >> value;
        prefix[i] = prefix[i - 1] + static_cast<std::uint32_t>(value);
    }
    for (int i = 0; i < q; ++i) {
        int left, right;
        std::cin >> left >> right;
        const std::uint32_t bits = prefix[right] - prefix[left - 1];
        std::int64_t sum = bits;
        if (bits > INT32_MAX) {
            sum -= std::int64_t{1} << 32;
        }
        std::cout << sum << '\n';
    }
}
