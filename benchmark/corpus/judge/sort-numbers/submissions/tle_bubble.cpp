#include <iostream>
#include <utility>
#include <vector>

// 冒泡排序：n = 1000 时很快，n = 10^5 时远超 CPU 限制。
int main() {
    int n;
    std::cin >> n;
    std::vector<long long> v(n);
    for (auto &x : v) std::cin >> x;
    for (int i = 0; i < n; ++i)
        for (int j = 0; j + 1 < n - i; ++j)
            if (v[j] > v[j + 1]) std::swap(v[j], v[j + 1]);
    for (int i = 0; i < n; ++i) std::cout << v[i] << (i + 1 < n ? " " : "\n");
}
