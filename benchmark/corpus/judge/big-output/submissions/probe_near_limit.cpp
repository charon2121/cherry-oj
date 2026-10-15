#include <cstdio>
#include <cstring>
#include <unistd.h>

// 探测项：输出约 1 MiB 减一点，观察输出上限的边界行为，不进断言。
int main() {
    static char block[4096];
    memset(block, 'x', sizeof block);
    for (int i = 0; i < 255; ++i)
        if (write(1, block, sizeof block) < 0) return 4;
}
