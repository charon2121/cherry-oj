// 执行器自身测试用的夹具程序：静态链接后放进测试 rootfs（/usr/bin/probe，另以 /usr/bin/true 的名字再放一份），
// 按第一个参数扮演最简单的几种程序。只服务于执行器协议与回收行为的测试；
// 用户提交的行为（身份、资源、系统调用、隔离）由 benchmark 的攻击样本检验。
#define _GNU_SOURCE
#include <libgen.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (!strcmp(basename(argv[0]), "true"))
        return 0;
    const char *mode = argc > 1 ? argv[1] : "exit";
    if (!strcmp(mode, "exit"))
        return argc > 2 ? atoi(argv[2]) : 0;
    // 不占 CPU 的程序：取消、同一 box 并发被拒时需要一个还没结束的执行。
    if (!strcmp(mode, "sleep")) {
        sleep(4);
        return 0;
    }
    if (!strcmp(mode, "cpu"))
        for (volatile unsigned long i = 0;; i++) {
        }
    // 提供可取回的产物。
    if (!strcmp(mode, "write")) {
        FILE *f = fopen("out.txt", "w");
        fputs("artifact\n", f);
        fclose(f);
        mkdir("sub", 0755);
        f = fopen("sub/deep.txt", "w");
        fputs("deep\n", f);
        fclose(f);
        return 0;
    }
    fprintf(stderr, "unknown probe mode %s\n", mode);
    return 2;
}
