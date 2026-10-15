#include <iostream>

// 结果正确，另往 stderr 写一行：stderr 不影响判定。
int main() {
    long long a, b;
    std::cin >> a >> b;
    std::cout << a + b << "\n";
    std::cerr << "benchmark-stderr\n";
}
