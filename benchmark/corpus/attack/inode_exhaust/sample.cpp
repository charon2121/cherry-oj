#include <cstdio>
#include <fcntl.h>
#include <unistd.h>

// 工作区 tmpfs 的 inode 上限是 4096：创建空文件直到失败，创建数必须在上限之内。
int main() {
    int created = 0;
    for (int i = 0; i < 100000; ++i) {
        char path[64];
        std::snprintf(path, sizeof path, "/work/f%d", i);
        int fd = open(path, O_CREAT | O_WRONLY, 0600);
        if (fd < 0) break;
        close(fd);
        ++created;
    }
    std::puts(created > 0 && created <= 4096 ? "limited" : "not-limited");
    return 0;
}
