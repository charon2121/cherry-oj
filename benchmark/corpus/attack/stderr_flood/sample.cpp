#include <cstring>
#include <unistd.h>

// 无限向 stderr 写数据，直到被终止。
int main() {
    static char block[4096];
    std::memset(block, 'x', sizeof block);
    for (;;)
        if (write(2, block, sizeof block) < 0) return 4;
}
