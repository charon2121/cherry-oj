#include <stdio.h>

// typeof 在 C23 之前是 GNU 扩展：-std=gnu17 能编译，严格的 -std=c11 不能。
int main(void) {
    long long a, b;
    if (scanf("%lld %lld", &a, &b) != 2) return 1;
    typeof(a) sum = a + b;
    printf("%lld\n", sum);
    return 0;
}
