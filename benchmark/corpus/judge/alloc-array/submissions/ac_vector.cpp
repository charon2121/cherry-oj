#include <iostream>
#include <vector>

int main() {
    int k;
    std::cin >> k;
    std::vector<char> v((size_t)k << 20, 1);
    std::cout << k + (v[0] - 1) << "\n";
}
