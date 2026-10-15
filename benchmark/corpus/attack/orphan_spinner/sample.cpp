#include <cstdio>
#include <unistd.h>

// 故意不等待：主进程立刻退出，留下一个 setsid 的睡眠后代。后代必须由执行器整组回收，不能等它自己退出。
int main() {
    pid_t child = fork();
    if (child == 0) {
        setsid();
        sleep(4);
        _exit(0);
    }
    std::printf("background-started %d\n", (int)child);
    return 0;
}
