#include <iostream>

// 用更宽的 __int128 累加，最后转回 long long 输出。
int main() {
    int n;
    std::cin >> n;
    __int128 sum = 0;
    for (int i = 0; i < n; ++i) {
        long long x;
        std::cin >> x;
        sum += x;
    }
    std::cout << static_cast<long long>(sum) << "\n";
}
