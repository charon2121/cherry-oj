#include <cstdio>
#include <cstdlib>

// 忽略输入，申请并写满 512 MiB：两倍于 256 MiB 的限制。
int main() {
    size_t bytes = (size_t)512 << 20;
    volatile unsigned char *p = (volatile unsigned char *)malloc(bytes);
    if (!p) return 3;
    for (size_t i = 0; i < bytes; i += 4096) p[i] = 1;
    printf("done\n");
}
