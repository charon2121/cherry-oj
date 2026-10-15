#include <unistd.h>

// 创建符号链接：过滤器不允许创建链接，走到返回就说明策略放行了它。
int main() { return symlink("/etc/passwd", "link") == 0 ? 0 : 5; }
