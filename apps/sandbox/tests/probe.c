// 测试用探针：静态链接后放进测试 rootfs 的 /usr/bin/probe，按第一个参数扮演不同的用户程序。
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

static void status_line(const char *key) {
    FILE *f = fopen("/proc/self/status", "r");
    char line[256];
    while (f && fgets(line, sizeof line, f))
        if (strncmp(line, key, strlen(key)) == 0 && line[strlen(key)] == ':') {
            char *v = line + strlen(key) + 1;
            while (*v == ' ' || *v == '\t')
                v++;
            v[strcspn(v, "\n")] = 0;
            printf("\"%s\":\"%s\",", key, v);
        }
    if (f)
        fclose(f);
}

int main(int argc, char **argv) {
    const char *mode = argc > 1 ? argv[1] : "";
    if (!strcmp(mode, "echo")) {
        char buf[4096];
        ssize_t n;
        while ((n = read(0, buf, sizeof buf)) > 0)
            fwrite(buf, 1, (size_t)n, stdout);
        fputs("probe-stderr\n", stderr);
        return 0;
    }
    if (!strcmp(mode, "exit"))
        return atoi(argv[2]);
    if (!strcmp(mode, "segv"))
        raise(SIGSEGV);
    if (!strcmp(mode, "spin"))
        for (volatile unsigned long i = 0;; i++) {
        }
    if (!strcmp(mode, "sleep")) {
        sleep(100);
        return 0;
    }
    if (!strcmp(mode, "hog")) {
        for (;;) {
            char *p = malloc(1 << 20);
            if (!p)
                return 3;
            memset(p, 1, 1 << 20);
        }
    }
    if (!strcmp(mode, "flood")) {
        static char buf[65536];
        memset(buf, 'x', sizeof buf);
        for (;;)
            if (write(1, buf, sizeof buf) < 0)
                return 4;
    }
    if (!strcmp(mode, "socket")) {
        socket(AF_INET, SOCK_STREAM, 0);
        return 0;
    }
    if (!strcmp(mode, "x32")) {
        syscall(0x40000000 | 39);
        return 0;
    }
    if (!strcmp(mode, "forks")) {
        int n = 0;
        while (n < 1000) {
            pid_t p = fork();
            if (p < 0)
                break;
            if (p == 0) {
                sleep(30);
                _exit(0);
            }
            n++;
        }
        printf("%d\n", n);
        return 0;
    }
    if (!strcmp(mode, "background")) {
        if (fork() == 0) {
            sleep(100);
            _exit(0);
        }
        return 0;
    }
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
    // 过滤器不允许创建链接或特殊文件；走到这里说明策略放行了它们。
    if (!strcmp(mode, "symlink"))
        return symlink("/etc/passwd", "link") == 0 ? 0 : 5;
    if (!strcmp(mode, "hardlink"))
        return link("/usr/bin/probe", "hard") == 0 ? 0 : 5;
    if (!strcmp(mode, "identity")) {
        // 先数继承下来的 FD，后面的 open 会产生新的 FD。
        int fds = 0;
        for (int fd = 3; fd < 256; fd++)
            fds += fcntl(fd, F_GETFD) >= 0;
        printf("{\"pid\":%d,\"ppid\":%d,\"uid\":%d,\"gid\":%d,\"extraFds\":%d,", getpid(), getppid(), getuid(),
               getgid(), fds);
        status_line("CapEff");
        status_line("CapPrm");
        status_line("CapBnd");
        status_line("CapAmb");
        status_line("NoNewPrivs");
        status_line("Seccomp");
        char cwd[256];
        printf("\"cwd\":\"%s\",", getcwd(cwd, sizeof cwd) ? cwd : "");
        printf("\"rootWritable\":%s,", open("/usr/probe-write", O_CREAT | O_WRONLY, 0644) >= 0 ? "true" : "false");
        printf("\"hostRootVisible\":%s,", access("/.oldroot/etc", F_OK) == 0 ? "true" : "false");
        printf("\"input\":%s,", access("/work/data/in.txt", R_OK) == 0 ? "true" : "false");
        printf("\"tmpWritable\":%s}\n", open("/tmp/x", O_CREAT | O_WRONLY, 0644) >= 0 ? "true" : "false");
        return 0;
    }
    fprintf(stderr, "unknown probe mode %s\n", mode);
    return 2;
}
