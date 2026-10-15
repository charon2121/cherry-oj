#include <cstdio>
#include <fcntl.h>
#include <unistd.h>

// /dev 里只有 null、zero、random、urandom：磁盘、内存、内核日志等设备都打不开。
int main() {
    const char *paths[] = {"/dev/sda", "/dev/vda", "/dev/nvme0n1", "/dev/mem", "/dev/kmem", "/dev/port", "/dev/kmsg",
                           "/dev/tty", "/dev/console"};
    for (const char *path : paths) {
        int fd = open(path, O_RDWR);
        if (fd < 0) fd = open(path, O_RDONLY);
        if (fd >= 0) {
            std::printf("opened: %s\n", path);
            return 1;
        }
    }
    std::puts("denied");
    return 0;
}
