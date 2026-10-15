#include <cstring>
#include <unistd.h>

// 忽略输入，无限输出，直到被终止。
int main() {
    static char block[4096];
    memset(block, 'x', sizeof block);
    for (;;)
        if (write(1, block, sizeof block) < 0) return 4;
}
