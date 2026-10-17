// CORE 驱动文件：judge 把它写成 Main.cpp，把提交写成 solution.cpp（文件名见 contracts/judge.schema.json 的 x-language-files）。
// 用户文件开头已经带着平台前缀（头文件与 using namespace std），所以先包含它。
#include "solution.cpp"

int main() {
    long long a, b;
    cin >> a >> b;
    cout << Solution().add(a, b) << "\n";
}
