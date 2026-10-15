#include <unistd.h>

// fork 一次后父子都死循环：CPU 超限按整组累计 CPU 判定，多进程不能靠分摊绕过上限。
int main() {
    if (fork() < 0) return 3;
    volatile unsigned long long x = 0;
    for (;;) x = x + 1;
}
