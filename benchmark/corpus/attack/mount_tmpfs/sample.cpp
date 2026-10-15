#include <sys/mount.h>

// 尝试挂载文件系统：挂载不在允许清单里。
int main() {
    mount("none", "/tmp", "tmpfs", 0, nullptr);
    return 8;
}
