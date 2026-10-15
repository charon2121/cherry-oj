#include <cstdlib>

// 不断申请 1 MiB 并每页写一个字节：只预留地址空间不算占内存，必须真的触碰物理页。
int main() {
    for (;;) {
        volatile char *p = (volatile char *)std::malloc(1 << 20);
        if (!p) return 3;
        for (int i = 0; i < (1 << 20); i += 4096) p[i] = 1;
    }
}
