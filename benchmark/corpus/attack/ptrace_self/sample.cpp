#include <sys/ptrace.h>

// 尝试 ptrace(PTRACE_TRACEME)：ptrace 不在允许清单里。
int main() {
    ptrace(PTRACE_TRACEME, 0, 0, 0);
    return 8;
}
