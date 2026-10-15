#include <cstdio>
#include <fcntl.h>
#include <unistd.h>

static bool readable(const char *path) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return false;
    close(fd);
    return true;
}

// 宿主上的东西从用户程序里不可达：宿主的 /proc 项、/sys、旧根下的文件都打不开。
int main() {
    const char *paths[] = {"/proc/1/environ", "/proc/1/mem", "/proc/kcore", "/proc/sys/kernel/hostname", "/sys/fs/cgroup",
                           "/sys/kernel", "/.oldroot/etc/passwd", "/.oldroot/proc/1/environ"};
    for (const char *path : paths)
        if (readable(path)) {
            std::printf("readable: %s\n", path);
            return 1;
        }
    std::puts("hidden");
    return 0;
}
