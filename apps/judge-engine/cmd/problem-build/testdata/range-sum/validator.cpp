#include <cstdint>
#include <iostream>

int main() {
    int n, q;
    if (!(std::cin >> n >> q) || n < 1 || n > 256 || q < 1 || q > 8) {
        std::cerr << "invalid n or q\n";
        return 1;
    }
    for (int i = 0; i < n; ++i) {
        std::int64_t value;
        if (!(std::cin >> value) || value < -1000000000 || value > 1000000000) {
            std::cerr << "invalid element " << i + 1 << '\n';
            return 1;
        }
    }
    for (int i = 0; i < q; ++i) {
        int left, right;
        if (!(std::cin >> left >> right) || left < 1 || left > right || right > n) {
            std::cerr << "invalid query " << i + 1 << '\n';
            return 1;
        }
    }
    std::cin >> std::ws;
    if (!std::cin.eof()) {
        std::cerr << "trailing data\n";
        return 1;
    }
}
