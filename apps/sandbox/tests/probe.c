// 测试用探针：静态链接后放进测试 rootfs（/usr/bin/probe，另以 /usr/bin/true 的名字再放一份），
// 按第一个参数扮演不同的用户程序。执行器自身的测试与 deploy/sandbox-linux 的内核套件共用它。
#define _GNU_SOURCE
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <libgen.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

// /proc 的值里有制表符，JSON 字符串不能直接包含控制字符。
static void json_field(const char *key, const char *value, int *first) {
    printf("%s\"%s\":\"", *first ? "" : ",", key);
    for (const char *c = value; *c; c++)
        if (*c == '\t')
            fputs("\\t", stdout);
        else
            putchar(*c);
    putchar('"');
    *first = 0;
}

// identity：身份、capability、过滤器状态，以及宿主文件与 FD 的可见性。
static int identity(void) {
    // 先数继承下来的 FD，后面的 open 会产生新的 FD。
    int extra = 0;
    for (int fd = 3; fd < 256; fd++)
        extra += fcntl(fd, F_GETFD) >= 0;
    int fds[64], nfds = 0;
    DIR *d = opendir("/proc/self/fd");
    for (struct dirent *e; d && (e = readdir(d)) && nfds < 64;)
        if (e->d_name[0] != '.')
            fds[nfds++] = atoi(e->d_name);
    if (d)
        closedir(d);
    printf("{\"status\":{");
    int first = 1;
    FILE *f = fopen("/proc/self/status", "r");
    char line[512];
    while (f && fgets(line, sizeof line, f)) {
        char *colon = strchr(line, ':');
        if (!colon)
            continue;
        *colon = 0;
        char *v = colon + 1;
        while (*v == ' ' || *v == '\t')
            v++;
        v[strcspn(v, "\n")] = 0;
        if (!strncmp(line, "Cap", 3) || !strcmp(line, "Uid") || !strcmp(line, "Gid") || !strcmp(line, "NoNewPrivs") ||
            !strcmp(line, "Seccomp") || !strcmp(line, "NSpid"))
            json_field(line, v, &first);
    }
    if (f)
        fclose(f);
    json_field("hostFileAbsent", access("/etc/hostname", F_OK) != 0 && errno == ENOENT ? "true" : "false", &first);
    int w = open("/usr/bin/escape", O_CREAT | O_WRONLY, 0600);
    json_field("rootWriteDenied", w < 0 ? "true" : "false", &first);
    printf("},\"fds\":[");
    first = 1;
    for (int i = 0; i < nfds; i++) {
        char path[64], target[256];
        snprintf(path, sizeof path, "/proc/self/fd/%d", fds[i]);
        ssize_t n = readlink(path, target, sizeof target - 1);
        if (n < 0)
            continue; // 读目录用的 FD 已经关闭
        target[n] = 0;
        printf("%s\"%s\"", first ? "" : ",", target);
        first = 0;
    }
    char cwd[256];
    int tmp = open("/tmp/probe", O_CREAT | O_WRONLY, 0600);
    printf("],\"pid\":%d,\"ppid\":%d,\"uid\":%d,\"gid\":%d,\"extraFds\":%d,\"cwd\":\"%s\",\"tmpWritable\":%s,"
           "\"hostRootVisible\":%s,\"input\":%s}\n",
           getpid(), getppid(), getuid(), getgid(), extra, getcwd(cwd, sizeof cwd) ? cwd : "",
           tmp >= 0 ? "true" : "false", access("/.oldroot/etc", F_OK) == 0 ? "true" : "false",
           access("/work/data/in.txt", R_OK) == 0 ? "true" : "false");
    return 0;
}

// isolation <left|right> <宿主 PID>：两个并发执行互相看不到对方的工作区，也看不到宿主进程。
static int isolation(int argc, char **argv) {
    if (argc != 4 || (strcmp(argv[2], "left") && strcmp(argv[2], "right")))
        return 2;
    const char *token = argv[2], *other = strcmp(token, "left") ? "left" : "right";
    char input[16] = {0}, name[32];
    int fd = open("own", O_RDONLY);
    if (fd < 0 || read(fd, input, sizeof input - 1) < 0 || strcmp(input, token))
        return fprintf(stderr, "input crossed executions\n"), 3;
    snprintf(name, sizeof name, "private-%s", token);
    fd = open(name, O_CREAT | O_WRONLY, 0600);
    if (fd < 0 || write(fd, token, strlen(token)) < 0)
        return 4;
    usleep(200000);
    snprintf(name, sizeof name, "private-%s", other);
    if (access(name, F_OK) == 0)
        return fprintf(stderr, "peer workspace visible\n"), 5;
    int pid = atoi(argv[3]);
    if (pid <= 100 || kill(pid, 0) == 0 || errno != ESRCH)
        return fprintf(stderr, "host process visible\n"), 6;
    printf("private %s\n", token);
    return 0;
}

static void *hold(void *arg) {
    (void)arg;
    sleep(4);
    return NULL;
}

static pid_t spawn_sleep(int detach) {
    pid_t p = fork();
    if (p == 0) {
        if (detach)
            setsid();
        execlp("probe", "probe", "sleep", (char *)NULL);
        _exit(127);
    }
    return p;
}

int main(int argc, char **argv) {
    if (!strcmp(basename(argv[0]), "true"))
        return 0;
    const char *mode = argc > 1 ? argv[1] : "identity";
    if (!strcmp(mode, "identity"))
        return identity();
    if (!strcmp(mode, "isolation"))
        return isolation(argc, argv);
    if (!strcmp(mode, "echo")) {
        char buf[4096];
        ssize_t n;
        while ((n = read(0, buf, sizeof buf)) > 0)
            fwrite(buf, 1, (size_t)n, stdout);
        fputs("probe-stderr\n", stderr);
        return 0;
    }
    if (!strcmp(mode, "exit"))
        return argc > 2 ? atoi(argv[2]) : 0;
    if (!strcmp(mode, "segv"))
        raise(SIGSEGV);
    if (!strcmp(mode, "privilege")) {
        if (setuid(0) == 0 || geteuid() == 0)
            return fprintf(stderr, "privilege escalation\n"), 3;
        puts("denied");
        return 0;
    }
    if (!strcmp(mode, "sleep")) {
        sleep(4);
        return 0;
    }
    if (!strcmp(mode, "background")) {
        // 故意不等待：主进程结束后的后代必须由执行器整组回收。
        printf("background-started %d\n", spawn_sleep(1));
        return 0;
    }
    if (!strcmp(mode, "processes")) {
        for (int n = 0; n < 80; n++)
            if (spawn_sleep(0) < 0) {
                printf("spawn-denied %d %s\n", n, strerror(errno));
                return 0;
            }
        return fprintf(stderr, "process limit did not reject bounded expansion\n"), 3;
    }
    if (!strcmp(mode, "threads")) {
        for (int n = 0; n < 80; n++) {
            pthread_t t;
            int e = pthread_create(&t, NULL, hold, NULL);
            if (e) {
                printf("threads-denied %d %s\n", n, strerror(e));
                return 0;
            }
        }
        return fprintf(stderr, "thread limit did not reject bounded expansion\n"), 3;
    }
    if (!strcmp(mode, "cpu"))
        for (volatile unsigned long i = 0;; i++) {
        }
    if (!strcmp(mode, "memory"))
        for (;;) {
            char *p = malloc(1 << 20);
            if (!p)
                return 3;
            for (int i = 0; i < (1 << 20); i += 4096)
                p[i] = 1;
        }
    if (!strcmp(mode, "output")) {
        static char buf[4096];
        memset(buf, 'x', sizeof buf);
        for (;;)
            if (write(1, buf, sizeof buf) < 0)
                return 4;
    }
    if (!strcmp(mode, "network"))
        return socket(AF_INET, SOCK_STREAM, 0) >= 0 ? 0 : 5;
    if (!strcmp(mode, "x32"))
        return (int)syscall(0x40000000 | 39) >= 0 ? 0 : 5;
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
    // 过滤器不允许创建链接；走到返回说明策略放行了它们。
    if (!strcmp(mode, "symlink"))
        return symlink("/etc/passwd", "link") == 0 ? 0 : 5;
    if (!strcmp(mode, "hardlink"))
        return link("/usr/bin/probe", "hard") == 0 ? 0 : 5;
    fprintf(stderr, "unknown probe mode %s\n", mode);
    return 2;
}
