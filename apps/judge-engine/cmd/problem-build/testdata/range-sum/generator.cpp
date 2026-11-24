#include <cstdint>
#include <iostream>
#include <random>
#include <string>

int main() {
    std::string test;
    if (!(std::cin >> test)) {
        return 1;
    }

    if (test == "sample") {
        std::cout << "5 3\n2 -3 8 0 -4\n1 3\n2 5\n4 4\n";
    } else if (test == "endpoints") {
        std::cout << "4 4\n5 0 -2 9\n1 4\n1 1\n4 4\n2 3\n";
    } else if (test == "single-negative") {
        std::cout << "1 1\n-7\n1 1\n";
    } else if (test == "overflow") {
        std::cout << "6 2\n1000000000 1000000000 1000000000 -1000000000 -1000000000 -1000000000\n1 3\n4 6\n";
    } else if (test == "maximum") {
        std::cout << "256 8\n";
        for (int i = 0; i < 256; ++i) {
            std::cout << (i == 0 ? "" : " ") << 1000000000;
        }
        std::cout << "\n1 256\n1 128\n129 256\n2 255\n128 129\n1 1\n256 256\n100 120\n";
    } else if (test == "random-small") {
        std::uint32_t seed;
        if (!(std::cin >> seed)) {
            return 1;
        }
        std::mt19937 random(seed);
        constexpr int n = 12;
        constexpr int q = 8;
        std::cout << n << ' ' << q << '\n';
        for (int i = 0; i < n; ++i) {
            std::cout << (i == 0 ? "" : " ") << (static_cast<int>(random() % 201) - 100);
        }
        std::cout << '\n';
        for (int i = 0; i < q; ++i) {
            const int left = 1 + random() % n;
            const int right = left + random() % (n - left + 1);
            std::cout << left << ' ' << right << '\n';
        }
    } else {
        std::cerr << "unknown test case\n";
        return 1;
    }
}
