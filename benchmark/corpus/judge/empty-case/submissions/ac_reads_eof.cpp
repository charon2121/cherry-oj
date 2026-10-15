#include <iostream>

// 读到 EOF；没有整数就什么都不输出，有就输出和。
int main() {
    long long x, sum = 0;
    int count = 0;
    while (std::cin >> x) {
        sum += x;
        ++count;
    }
    if (count > 0) std::cout << sum << "\n";
}
