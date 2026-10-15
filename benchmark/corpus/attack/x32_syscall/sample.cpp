#include <sys/syscall.h>
#include <unistd.h>

// 用 x32 ABI 的调用号（0x40000000 | 39）调用 getpid：过滤器不能被「换一种系统调用 ABI」绕开。
int main() { return (int)syscall(0x40000000 | 39) >= 0 ? 0 : 5; }
