#include <cstdio>
#include <unistd.h>

// 尝试 setuid(0)：成功就是提权，必须失败。setuid 通常不在过滤器的允许清单里被 SIGSYS 杀死；即使放行，也只能得到拒绝。
int main() {
    if (setuid(0) == 0 || geteuid() == 0) {
        std::fprintf(stderr, "privilege escalation\n");
        return 3;
    }
    std::puts("denied");
    return 0;
}
