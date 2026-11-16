#include <cerrno>
#include <sys/socket.h>

// 必须是被拒绝后退出，其他系统错误不能冒充网络隔离成功。
int main() { return socket(AF_INET, SOCK_STREAM, 0) >= 0 ? 0 : (errno == EPERM ? 5 : 6); }
