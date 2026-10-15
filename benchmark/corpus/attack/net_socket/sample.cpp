#include <sys/socket.h>

// 创建网络 socket：被 seccomp 禁止，进程被 SIGSYS 杀死。
int main() { return socket(AF_INET, SOCK_STREAM, 0) >= 0 ? 0 : 5; }
