// CORE 驱动文件（C）：judge 把它写成 Main.c，把提交写成 solution.c（文件名见 contracts/languages.json 的 files）。
// 用户文件开头已经带着平台前缀（常用头文件），所以先包含它。
#include "solution.c"

int main(void) {
    long long a, b;
    if (scanf("%lld %lld", &a, &b) != 2) return 1;
    printf("%lld\n", add(a, b));
    return 0;
}
