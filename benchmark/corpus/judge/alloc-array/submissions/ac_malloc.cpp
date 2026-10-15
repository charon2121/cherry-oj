#include <cstdio>
#include <cstdlib>

// 申请 k MiB 并每页写一个字节：只预留地址空间不算占内存。
int main() {
    int k;
    if (scanf("%d", &k) != 1) return 2;
    size_t bytes = (size_t)k << 20;
    volatile unsigned char *p = (volatile unsigned char *)malloc(bytes);
    if (!p) return 3;
    for (size_t i = 0; i < bytes; i += 4096) p[i] = 1;
    printf("%d\n", k);
    free((void *)p);
}
