#include <cstring>
#include <fcntl.h>
#include <unistd.h>

// 单文件大小上限是 64 MiB：写 65 MiB 的单个文件，超过时内核发 SIGXFSZ 终止进程。
int main() {
    static char block[1 << 20];
    std::memset(block, 'x', sizeof block);
    int fd = open("/work/big", O_CREAT | O_WRONLY, 0600);
    if (fd < 0) return 4;
    for (int i = 0; i < 65; ++i)
        if (write(fd, block, sizeof block) < 0) return 5;
    return 0;
}
