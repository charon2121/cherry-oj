#include <iostream>

// 只在第 3 个测试点（10 20）崩溃。
int main() {
    long long a, b;
    std::cin >> a >> b;
    if (a == 10) return 7;
    std::cout << a + b << "\n";
}
