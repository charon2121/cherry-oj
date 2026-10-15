#include <cerrno>
#include <csignal>
#include <cstdio>
#include <unistd.h>

// 沙箱里还有执行器的 init（另一个 uid）：向它和任何不属于自己的进程发信号，都必须被拒绝。
int main() {
    for (int pid = 1; pid < 64; ++pid) {
        if (pid == getpid()) continue;
        if (kill(pid, SIGKILL) == 0) {
            std::printf("signalled: %d\n", pid);
            return 1;
        }
    }
    std::puts("denied");
    return 0;
}
