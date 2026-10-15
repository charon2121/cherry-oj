#include <iostream>

// 行尾多一个空格：N␠\n
// 同一份源码有 _loose 与 _strict 两个文件：一个提交只有一个预期，靠 requires 区分节点的 strictWhitespace 配置。
int main() {
    long long a, b;
    std::cin >> a >> b;
    std::cout << a + b << " \n";
}
