#include <cstdint>
#include <iostream>
#include <vector>

int main() {
    int n, q;
    std::cin >> n >> q;
    std::vector<std::int64_t> prefix(n + 1);
    for (int i = 1; i <= n; ++i) {
        std::int64_t value;
        std::cin >> value;
        prefix[i] = prefix[i - 1] + value;
    }
    for (int i = 0; i < q; ++i) {
        int left, right;
        std::cin >> left >> right;
        std::cout << prefix[right] - prefix[left] << '\n';
    }
}
