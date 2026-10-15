#include <csignal>

// 向自己发 SIGKILL：SIGKILL 本身不是 CPU 超限或 OOM 的证据，不能被误判成 TLE 或 MLE。
int main() { return std::raise(SIGKILL); }
