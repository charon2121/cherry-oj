#include <iostream>

// 读入 long long，却用 int 累加：每一步加法在 long long 里做，再截断成 int。
// 只有总和越过 int 范围的测试点才会错。
int main() {
    int n;
    std::cin >> n;
    int sum = 0;
    for (int i = 0; i < n; ++i) {
        long long x;
        std::cin >> x;
        sum += x;
    }
    std::cout << sum << "\n";
}
