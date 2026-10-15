#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>

// 工作区是 128 MiB 的 tmpfs。每个文件写 32 MiB（低于单文件上限 64 MiB），写满为止：
// 总量必须被 tmpfs 容量卡住，而不是写满宿主磁盘。
int main() {
    static char block[1 << 20];
    std::memset(block, 'x', sizeof block);
    long long total = 0;
    for (int file = 0; file < 16; ++file) {
        char path[64];
        std::snprintf(path, sizeof path, "/work/fill-%d", file);
        int fd = open(path, O_CREAT | O_WRONLY, 0600);
        if (fd < 0) break;
        for (int i = 0; i < 32; ++i) {
            ssize_t n = write(fd, block, sizeof block);
            if (n < 0) {
                if (errno == ENOSPC && total <= (128LL << 20)) std::puts("full");
                return 0;
            }
            total += n;
        }
    }
    std::puts("not-limited");
    return 0;
}
