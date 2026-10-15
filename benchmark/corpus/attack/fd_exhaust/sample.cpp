#include <cstdio>
#include <fcntl.h>
#include <unistd.h>

// 打开文件数上限是 256：不断 open 直到失败，成功数必须在上限之内。
int main() {
    int opened = 0;
    for (int i = 0; i < 100000; ++i) {
        if (open("/dev/null", O_RDONLY) < 0) break;
        ++opened;
    }
    std::puts(opened > 0 && opened <= 256 ? "limited" : "not-limited");
    return 0;
}
