#include <unistd.h>

// 创建指向 /proc/self/fd/0 的符号链接（魔法链接）：同样不能创建。
int main() { return symlink("/proc/self/fd/0", "out") == 0 ? 0 : 5; }
