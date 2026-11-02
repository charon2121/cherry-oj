#include <math.h>
#include <stdio.h>

// 用到 sqrt：编译命令少了 -lm 就会链接失败（参数来自输入，编译期算不出来）。
int main(void) {
    long long a, b;
    if (scanf("%lld %lld", &a, &b) != 2) return 1;
    double magnitude = sqrt((double)(a * a + b * b));
    if (magnitude < 0) return 1;
    printf("%lld\n", a + b);
    return 0;
}
