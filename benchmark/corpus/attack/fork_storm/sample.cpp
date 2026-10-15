#include <cerrno>
#include <cstdio>
#include <cstring>
#include <unistd.h>

// 不断 fork 睡眠的子进程，最多 80 个：进程数上限要有界地拒绝扩张，程序自己能观察到被拒。
int main() {
    for (int n = 0; n < 80; ++n) {
        pid_t child = fork();
        if (child < 0) {
            std::printf("spawn-denied %d %s\n", n, std::strerror(errno));
            return 0;
        }
        if (child == 0) {
            sleep(4);
            _exit(0);
        }
    }
    std::fprintf(stderr, "process limit did not reject bounded expansion\n");
    return 3;
}
