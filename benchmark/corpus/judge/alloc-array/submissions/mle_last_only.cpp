#include <cstdio>
#include <cstdlib>

// k >= 100 时把申请量放大到 6 倍（600 MiB），其余测试点正常：同一份提交逐点结论不同。
int main() {
    int k;
    if (scanf("%d", &k) != 1) return 2;
    size_t bytes = (size_t)(k >= 100 ? k * 6 : k) << 20;
    volatile unsigned char *p = (volatile unsigned char *)malloc(bytes);
    if (!p) return 3;
    for (size_t i = 0; i < bytes; i += 4096) p[i] = 1;
    printf("%d\n", k);
    free((void *)p);
}
