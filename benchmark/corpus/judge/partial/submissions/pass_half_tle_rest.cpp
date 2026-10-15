#include <iostream>

// 前 3 个测试点正确，后 3 个（|a| >= 10^6）死循环。
int main() {
    long long a, b;
    std::cin >> a >> b;
    if (a >= 1000000 || a <= -1000000) { volatile unsigned long long x = 0; for (;;) x = x + 1; }
    std::cout << a + b << "\n";
}
