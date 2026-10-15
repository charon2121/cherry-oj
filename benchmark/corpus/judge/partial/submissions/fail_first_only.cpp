#include <iostream>

// 只在第 1 个测试点（1 1）算错。
int main() {
    long long a, b;
    std::cin >> a >> b;
    if (a == 1 && b == 1) { std::cout << 0 << "\n"; return 0; }
    std::cout << a + b << "\n";
}
