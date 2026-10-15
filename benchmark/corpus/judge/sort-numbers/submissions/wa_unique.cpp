#include <algorithm>
#include <iostream>
#include <vector>

// 排序后去重：只有含重复的测试点才会错。
int main() {
    int n;
    std::cin >> n;
    std::vector<long long> v(n);
    for (auto &x : v) std::cin >> x;
    std::sort(v.begin(), v.end());
    v.erase(std::unique(v.begin(), v.end()), v.end());
    for (size_t i = 0; i < v.size(); ++i) std::cout << v[i] << (i + 1 < v.size() ? " " : "\n");
}
