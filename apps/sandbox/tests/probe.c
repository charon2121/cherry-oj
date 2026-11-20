// 执行器自身测试用的夹具程序：静态链接后放进测试 rootfs（/usr/bin/probe，另以 /usr/bin/true 的名字再放一份），
// 按第一个参数扮演最简单的几种程序。只服务于执行器协议与回收行为的测试；
// 用户提交的行为（身份、资源、系统调用、隔离）由 benchmark 的攻击样本检验。
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/magic.h>
#include <libgen.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <sys/sendfile.h>
#include <sys/resource.h>
#include <sys/statvfs.h>
#include <sys/vfs.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

static int check_core_limit(void) {
    struct rlimit current;
    if (getrlimit(RLIMIT_CORE, &current) || current.rlim_cur != 1 || current.rlim_max != 1)
        return 3;
    // resource 在内核中是 32 位；同时覆盖正常 libc、直接 syscall 和高位伪装。
    const uint64_t resources[] = {RLIMIT_CORE, (UINT64_C(1) << 32) | RLIMIT_CORE,
                                  UINT64_C(0xffffffff00000000) | RLIMIT_CORE};
    const rlim_t values[] = {0, 1, RLIM_INFINITY};
    for (size_t i = 0; i < sizeof resources / sizeof resources[0]; i++) {
        for (size_t j = 0; j < sizeof values / sizeof values[0]; j++) {
            struct rlimit replacement = {values[j], values[j]};
            if (syscall(SYS_setrlimit, resources[i], &replacement) != -1 || errno != EPERM)
                return 4;
            for (int target = 0; target < 2; target++)
                if (syscall(SYS_prlimit64, target ? getpid() : 0, resources[i], &replacement, &current) != -1 ||
                    errno != EPERM)
                    return 5;
        }
        if (syscall(SYS_prlimit64, 0, resources[i], NULL, &current) || current.rlim_cur != 1 || current.rlim_max != 1)
            return 6;
    }
    struct rlimit unchanged = {1, 1};
    if (setrlimit(RLIMIT_CORE, &unchanged) != -1 || errno != EPERM)
        return 7;
    // 查询与其他限制的修改仍交给内核；不能为封住 core 而一起禁止它们。
    if (getrlimit(RLIMIT_NOFILE, &current) || setrlimit(RLIMIT_NOFILE, &current) ||
        syscall(SYS_prlimit64, 0, (UINT64_C(1) << 32) | RLIMIT_NOFILE, &current, NULL))
        return 8;
    if (syscall(SYS_setrlimit, UINT32_MAX, &current) != -1 || errno != EINVAL)
        return 9;
    if (syscall(SYS_prlimit64, 0, UINT32_MAX, &current, NULL) != -1 || errno != EINVAL)
        return 10;
    return 0;
}

int main(int argc, char **argv) {
    if (!strcmp(basename(argv[0]), "true"))
        return 0;
    const char *mode = argc > 1 ? argv[1] : "exit";
    if (!strcmp(mode, "exit"))
        return argc > 2 ? atoi(argv[2]) : 0;
    if (!strcmp(mode, "core-limit"))
        return check_core_limit();
    if (!strcmp(mode, "core-limit-exec")) {
        execl("/usr/bin/probe", "probe", "core-limit", NULL);
        return 11;
    }
    if (!strcmp(mode, "core-limit-fork")) {
        pid_t child = fork();
        if (child < 0)
            return 11;
        if (child == 0)
            _exit(check_core_limit());
        int status;
        if (waitpid(child, &status, 0) != child || !WIFEXITED(status))
            return 12;
        return WEXITSTATUS(status);
    }
    if (!strcmp(mode, "abort"))
        abort();
    if (!strcmp(mode, "segv")) {
        raise(SIGSEGV);
        return 13;
    }
    if (!strcmp(mode, "mount")) {
        syscall(SYS_mount, "none", "/tmp", "tmpfs", 0, NULL);
        return 13;
    }
    // 不占 CPU 的程序：取消、同一 box 并发被拒时需要一个还没结束的执行。
    if (!strcmp(mode, "sleep")) {
        sleep(4);
        return 0;
    }
    if (!strcmp(mode, "unix-probe") || !strcmp(mode, "ipv4") || !strcmp(mode, "ipv6")) {
        int domain = !strcmp(mode, "unix-probe") ? AF_UNIX : (!strcmp(mode, "ipv4") ? AF_INET : AF_INET6);
        int fd = socket(domain, SOCK_STREAM | SOCK_CLOEXEC, 0);
        if (fd >= 0) {
            close(fd);
            return 3;
        }
        return errno == EPERM ? 0 : 4;
    }
    if (!strcmp(mode, "socketpair")) {
        int pair[2];
        return socketpair(AF_UNIX, SOCK_STREAM, 0, pair) < 0 ? 4 : 3;
    }
    if (!strcmp(mode, "connect"))
        return connect(-1, NULL, 0) < 0 ? 4 : 3;
    if (!strcmp(mode, "filesystem")) {
        struct statfs work, descriptor, root;
        int fd = open(".", O_RDONLY | O_DIRECTORY | O_CLOEXEC);
        if (fd < 0 || statfs("/work", &work) || fstatfs(fd, &descriptor) || statfs("/", &root))
            return 3;
        close(fd);
        return work.f_type == TMPFS_MAGIC && descriptor.f_type == TMPFS_MAGIC &&
                       (unsigned long)work.f_bsize * work.f_blocks == (128UL << 20) && (root.f_flags & ST_RDONLY)
                   ? 0
                   : 4;
    }
    if (!strcmp(mode, "copy-fallback")) {
        int in = open("copy-in", O_CREAT | O_RDWR | O_CLOEXEC, 0600);
        int out = open("copy-out", O_CREAT | O_RDWR | O_CLOEXEC, 0600);
        if (in < 0 || out < 0 || write(in, "copy", 4) != 4 || lseek(in, 0, SEEK_SET) < 0)
            return 3;
        if (copy_file_range(in, NULL, out, NULL, 4096, 0) != -1 || errno != ENOSYS)
            return 4;
        if (sendfile(out, in, NULL, 4096) != -1 || errno != ENOSYS)
            return 5;
        if (fchown(out, getuid(), getgid()) != -1 || errno != EPERM)
            return 6;
        char data[4];
        if (read(in, data, sizeof data) != 4 || write(out, data, sizeof data) != 4)
            return 7;
        close(in);
        close(out);
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
