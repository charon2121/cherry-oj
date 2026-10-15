#include <iostream>

// 只在第 6 个测试点（123456789 987654321）算错。
int main() {
    long long a, b;
    std::cin >> a >> b;
    if (a == 123456789) { std::cout << 0 << "\n"; return 0; }
    std::cout << a + b << "\n";
}
