#include <fcntl.h>
#include <unistd.h>

// 先创建一个文件，再对它创建硬链接：不能创建。
int main() {
    int file = open("first", O_CREAT | O_WRONLY, 0600);
    if (file < 0) return 4;
    write(file, "x", 1);
    close(file);
    return link("first", "out") == 0 ? 0 : 5;
}
