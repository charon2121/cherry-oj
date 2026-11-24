#include <cstdint>
#include <iostream>
#include <vector>

int main() {
    int n, q;
    std::cin >> n >> q;
    std::vector<std::int64_t> values(n);
    for (auto& value : values) {
        std::cin >> value;
    }
    while (q-- > 0) {
        int left, right;
        std::cin >> left >> right;
        std::int64_t sum = 0;
        for (int i = left - 1; i < right; ++i) {
            sum += values[i];
        }
        std::cout << sum << '\n';
    }
}
