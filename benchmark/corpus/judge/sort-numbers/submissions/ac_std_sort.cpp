#include <algorithm>
#include <iostream>
#include <vector>

int main() {
    std::ios::sync_with_stdio(false);
    std::cin.tie(nullptr);
    int n;
    std::cin >> n;
    std::vector<long long> v(n);
    for (auto &x : v) std::cin >> x;
    std::sort(v.begin(), v.end());
    for (int i = 0; i < n; ++i) std::cout << v[i] << (i + 1 < n ? " " : "\n");
}
