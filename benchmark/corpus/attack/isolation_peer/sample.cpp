#include <cerrno>
#include <csignal>
#include <cstdio>
#include <fcntl.h>
#include <iostream>
#include <string>
#include <unistd.h>

// 标准输入是「<标记> <宿主上运行者的进程号>」。两个并发的执行互相看不到对方的工作区，也看不到宿主上的进程。
// 退出码区分是哪一类隔离失效：4 写不了自己的文件，5 看见了对方的工作区，6 看见了宿主进程。
int main() {
    std::string token;
    long host = 0;
    if (!(std::cin >> token >> host) || (token != "left" && token != "right") || host <= 1) return 2;
    std::string other = token == "left" ? "right" : "left";
    int file = open(("private-" + token).c_str(), O_CREAT | O_WRONLY, 0600);
    if (file < 0 || write(file, token.data(), token.size()) < 0) return 4;
    sleep(1);  // 让两个执行真的重叠
    if (access(("private-" + other).c_str(), F_OK) == 0) {
        std::fprintf(stderr, "peer workspace visible\n");
        return 5;
    }
    if (kill((pid_t)host, 0) == 0 || errno != ESRCH) {
        std::fprintf(stderr, "host process visible\n");
        return 6;
    }
    std::printf("private %s peers=0 host=hidden\n", token.c_str());
    return 0;
}
