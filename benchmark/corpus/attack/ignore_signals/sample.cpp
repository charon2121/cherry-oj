#include <csignal>

// 忽略 SIGTERM 与 SIGINT 后死循环：超限时终止不能靠信号，躲不开。
int main() {
    std::signal(SIGTERM, SIG_IGN);
    std::signal(SIGINT, SIG_IGN);
    volatile unsigned long long x = 0;
    for (;;) x = x + 1;
}
