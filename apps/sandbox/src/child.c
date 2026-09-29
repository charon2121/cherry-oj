// namespace 内的两个进程。
//
// init 是新 PID namespace 的 PID 1，以 root 身份依次：挂只读 rootfs、/work、/tmp、/proc、/dev，
// pivot_root，把输入复制进 /work，然后 fork 出用户程序；之后降到 init 身份、装上不能 execve 的
// 过滤器，只做两件事：报告用户程序的退出，回收孤儿进程。init 退出时内核会杀死整个 namespace。
//
// 用户程序进程设置 rlimit、降到 payload 身份、装过滤器，然后 execve。任何一步失败都向
// exec_error 管道写 8 字节记录后退出；该管道带 CLOEXEC，execve 成功时自动关闭，
// 父进程读到 EOF 就知道用户程序已经开始运行——这就是计时起点。
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <linux/mount.h>
#include <linux/openat2.h>
#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <sys/wait.h>
#include <unistd.h>

#include "sandbox.h"

// init 失败时报告的阶段，与错误码一起写进 report 管道。
enum phase {
    PHASE_ROOTFS = 1,
    PHASE_WORKSPACE,
    PHASE_SYSTEM_VIEWS,
    PHASE_PIVOT,
    PHASE_INPUTS,
    PHASE_COMMAND,
    PHASE_FORK,
    PHASE_SUPERVISOR,
    PHASE_WAIT,
};

// 用户程序 execve 前的失败阶段。
enum exec_stage { STAGE_STDIO = 1, STAGE_RLIMIT, STAGE_CLOEXEC, STAGE_PRIVILEGES, STAGE_SECCOMP, STAGE_EXECVE };

static int report_fd = -1;

static _Noreturn void init_fail(enum phase phase) {
    struct report r = {.kind = REPORT_INIT_FAILED, .a = (int32_t)phase, .b = errno};
    (void)write_all(report_fd, &r, sizeof r);
    _exit(FAILURE_EXIT_CODE);
}

#define TRY(phase, expr)                                                                                               \
    do {                                                                                                               \
        if ((expr) != 0)                                                                                               \
            init_fail(phase);                                                                                          \
    } while (0)

static int join(char *out, size_t cap, const char *root, const char *path) {
    int n = snprintf(out, cap, "%s/%s", root, path);
    return n < 0 || (size_t)n >= cap ? -1 : 0;
}

// 挂载点都由可信的 rootfs 制作器预建；禁止是链接，避免把挂载引到别处。
static int check_mount_point(const char *root, const char *path) {
    char p[PATH_MAX];
    struct stat st;
    if (join(p, sizeof p, root, path) || lstat(p, &st) != 0)
        return -1;
    return S_ISDIR(st.st_mode) ? 0 : (errno = ENOTDIR, -1);
}

static void prepare_root(const struct child_plan *plan) {
    const char *root = plan->config->rootfs;
    char p[PATH_MAX];
    TRY(PHASE_ROOTFS, mount(NULL, "/", NULL, MS_REC | MS_PRIVATE, NULL));
    TRY(PHASE_ROOTFS, sethostname("cherry-sandbox", 14));
    TRY(PHASE_ROOTFS, mount(root, root, NULL, MS_BIND, NULL));
    static const char *points[] = {"work", "tmp", "proc", "dev", ".oldroot"};
    for (size_t i = 0; i < sizeof points / sizeof points[0]; i++)
        TRY(PHASE_ROOTFS, check_mount_point(root, points[i]));
    TRY(PHASE_ROOTFS, mount(NULL, root, NULL, MS_BIND | MS_REMOUNT | MS_RDONLY | MS_NOSUID | MS_NODEV, NULL));

    // 工作区是父进程用 fsmount 建好的独立 tmpfs，这里只把它接到 /work。
    // /tmp 是同一 tmpfs 的子目录，临时文件仍计入同一容量与 inode 预算。
    TRY(PHASE_WORKSPACE, join(p, sizeof p, root, "work"));
    TRY(PHASE_WORKSPACE, (int)syscall(SYS_move_mount, plan->workspace, "", AT_FDCWD, p, MOVE_MOUNT_F_EMPTY_PATH));
    TRY(PHASE_WORKSPACE, join(p, sizeof p, root, "work/.tmp"));
    TRY(PHASE_WORKSPACE, mkdir(p, 01777));
    TRY(PHASE_WORKSPACE, chmod(p, 01777));
    char tmp[PATH_MAX];
    TRY(PHASE_WORKSPACE, join(tmp, sizeof tmp, root, "tmp"));
    TRY(PHASE_WORKSPACE, mount(p, tmp, NULL, MS_BIND, NULL));

    TRY(PHASE_SYSTEM_VIEWS, join(p, sizeof p, root, "proc"));
    TRY(PHASE_SYSTEM_VIEWS, mount("proc", p, "proc", MS_NOSUID | MS_NODEV | MS_NOEXEC | MS_RDONLY, "hidepid=2,subset=pid"));
    // 只重建最小 /dev（固定的内存设备号），不暴露宿主设备树。
    TRY(PHASE_SYSTEM_VIEWS, join(p, sizeof p, root, "dev"));
    TRY(PHASE_SYSTEM_VIEWS, mount("tmpfs", p, "tmpfs", MS_NOSUID | MS_NOEXEC, "size=4096,nr_inodes=8,mode=0755"));
    static const struct {
        const char *name;
        unsigned minor;
    } devices[] = {{"null", 3}, {"zero", 5}, {"random", 8}, {"urandom", 9}};
    for (size_t i = 0; i < sizeof devices / sizeof devices[0]; i++) {
        char d[PATH_MAX];
        TRY(PHASE_SYSTEM_VIEWS, join(d, sizeof d, p, devices[i].name));
        TRY(PHASE_SYSTEM_VIEWS, mknod(d, S_IFCHR | 0666, makedev(1, devices[i].minor)));
        TRY(PHASE_SYSTEM_VIEWS, chmod(d, 0666));
    }
    TRY(PHASE_SYSTEM_VIEWS, mount(NULL, p, NULL, MS_REMOUNT | MS_RDONLY | MS_NOSUID | MS_NOEXEC, NULL));

    TRY(PHASE_PIVOT, join(p, sizeof p, root, ".oldroot"));
    TRY(PHASE_PIVOT, (int)syscall(SYS_pivot_root, root, p));
    // pivot_root 后旧工作目录仍可能引用旧根：先切到新根，再断开旧根。
    TRY(PHASE_PIVOT, chdir("/"));
    TRY(PHASE_PIVOT, umount2("/.oldroot", MNT_DETACH));
}

static int openat2_beneath(int dir, const char *path, int flags, mode_t mode) {
    struct open_how how = {.flags = (uint64_t)flags | O_CLOEXEC,
                           .mode = mode,
                           .resolve = RESOLVE_BENEATH | RESOLVE_NO_SYMLINKS | RESOLVE_NO_MAGICLINKS | RESOLVE_NO_XDEV};
    return (int)syscall(SYS_openat2, dir, path, &how, sizeof how);
}

static int copy_exact(int src, int dst, int64_t size) {
    char buf[64 << 10];
    while (size > 0) {
        ssize_t n = read(src, buf, size < (int64_t)sizeof buf ? (size_t)size : sizeof buf);
        if (n < 0 && errno == EINTR)
            continue;
        if (n <= 0)
            return errno = EIO, -1;
        if (write_all(dst, buf, (size_t)n) != 0)
            return -1;
        size -= n;
    }
    // 源文件在复制期间变长也算失败：交付的必须正是检查时的那一份。
    return read(src, buf, 1) == 0 ? 0 : (errno = EIO, -1);
}

// 逐层创建目录并用目录 FD 打开下一层；已有目录也要重新打开核验，不能只因 EEXIST 就信任。
static int put_input(int work, const char *path, bool executable, int src, int64_t size, uid_t uid, gid_t gid) {
    char copy[1024];
    strcpy(copy, path);
    int dir = dup(work);
    char *part = copy;
    for (char *slash; dir >= 0 && (slash = strchr(part, '/')); part = slash + 1) {
        *slash = 0;
        if (mkdirat(dir, part, 0755) != 0 && errno != EEXIST)
            break;
        int next = openat2_beneath(dir, part, O_RDONLY | O_DIRECTORY, 0);
        close(dir);
        dir = next;
        if (dir >= 0 && fchown(dir, uid, gid) != 0) {
            close(dir);
            dir = -1;
        }
    }
    if (dir < 0 || strchr(part, '/'))
        return -1;
    int fd = openat2_beneath(dir, part, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, 0600);
    close(dir);
    if (fd < 0)
        return -1;
    // 先改权限再改所有者：交出所有权之后，root 再改权限需要 CAP_FOWNER，而执行器不持有它。
    int rc = copy_exact(src, fd, size);
    if (rc == 0)
        rc = fchmod(fd, executable ? 0755 : 0644);
    if (rc == 0)
        rc = fchown(fd, uid, gid);
    return close(fd) != 0 ? -1 : rc;
}

// 输入在本执行组的计量下写入工作区。stdin 写成文件后重开为只读并删除：
// 用户程序能读 FD 0，却不能通过文件名改写这份输入。
static int load_inputs(const struct child_plan *plan, uid_t uid, gid_t gid, int *stdin_fd) {
    int work = open("/work", O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    if (work < 0)
        return -1;
    int64_t budget = MAX_INPUT_BYTES;
    for (int i = 0; i < plan->spec->ninputs; i++) {
        int64_t size;
        int src = box_open_input(plan->box, plan->config, i, &size);
        if (src < 0 || size > budget) {
            if (src >= 0)
                close(src);
            close(work);
            errno = src < 0 ? errno : EFBIG;
            return -1;
        }
        budget -= size;
        int rc = put_input(work, plan->spec->inputs[i].path, plan->spec->inputs[i].executable, src, size, uid, gid);
        close(src);
        if (rc != 0) {
            close(work);
            return -1;
        }
    }
    struct stat st;
    if (fstat(plan->box->stdin_file, &st) != 0 || st.st_size > budget) {
        close(work);
        return errno = EFBIG, -1;
    }
    int w = openat2_beneath(work, ".stdin", O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, 0600);
    int rc = w < 0 ? -1 : copy_exact(plan->box->stdin_file, w, st.st_size);
    if (w >= 0 && close(w) != 0)
        rc = -1;
    *stdin_fd = rc == 0 ? openat2_beneath(work, ".stdin", O_RDONLY, 0) : -1;
    if (*stdin_fd < 0 || unlinkat(work, ".stdin", 0) != 0)
        rc = -1;
    close(work);
    return rc;
}

// 命令先在工作区找（编译产物），再在只读 rootfs 的固定目录找；请求中的 PATH 不参与解析。
static int resolve_command(const char *name, char *out, size_t cap) {
    static const char *dirs[] = {"/work", "/usr/bin", "/bin"};
    for (size_t i = 0; i < sizeof dirs / sizeof dirs[0]; i++) {
        struct stat st;
        if (join(out, cap, dirs[i], name) != 0)
            return -1;
        if (stat(out, &st) == 0 && S_ISREG(st.st_mode) && (st.st_mode & 0111))
            return 0;
        if (errno != ENOENT && errno != ENOTDIR)
            return -1;
    }
    return errno = ENOENT, -1;
}

static _Noreturn void exec_fail(int fd, enum exec_stage stage) {
    struct exec_failure f = {.stage = stage, .err = errno};
    (void)write_all(fd, &f, sizeof f);
    _exit(FAILURE_EXIT_CODE);
}

static _Noreturn void payload_main(const struct child_plan *plan, const char *path, int stdin_fd) {
    int errfd = plan->exec_error_w;
    if (dup2(stdin_fd, 0) < 0 || dup2(plan->stdout_w, 1) < 0 || dup2(plan->stderr_w, 2) < 0 || chdir("/work") != 0)
        exec_fail(errfd, STAGE_STDIO);
    const struct {
        int resource;
        rlim_t value;
    } limits[] = {{RLIMIT_CORE, 0}, {RLIMIT_NOFILE, PAYLOAD_NOFILE}, {RLIMIT_FSIZE, PAYLOAD_FSIZE}};
    for (size_t i = 0; i < sizeof limits / sizeof limits[0]; i++) {
        struct rlimit r = {limits[i].value, limits[i].value};
        if (setrlimit(limits[i].resource, &r) != 0)
            exec_fail(errfd, STAGE_RLIMIT);
    }
    // 3 号以上的 FD（box 目录、工作区、管道）都不能跨过 execve 留给用户程序。
    if (syscall(SYS_close_range, 3, ~0U, CLOSE_RANGE_CLOEXEC) != 0)
        exec_fail(errfd, STAGE_CLOEXEC);
    int slot = plan->box_index;
    if (drop_privileges(plan->config->payload_uid + (uid_t)slot, plan->config->payload_gid + (gid_t)slot) != 0)
        exec_fail(errfd, STAGE_PRIVILEGES);
    if (filter_install(FILTER_PAYLOAD) != 0)
        exec_fail(errfd, STAGE_SECCOMP);
    static const char *fixed_env[] = {"PATH=/usr/bin:/bin", "HOME=/work", "TMPDIR=/tmp", "LANG=C"};
    char *envp[MAX_ENV + 5];
    int n = 0;
    for (size_t i = 0; i < sizeof fixed_env / sizeof fixed_env[0]; i++)
        envp[n++] = (char *)fixed_env[i];
    for (int i = 0; i < plan->spec->nenv; i++)
        envp[n++] = plan->spec->env[i];
    envp[n] = NULL;
    execve(path, plan->spec->args, envp);
    exec_fail(errfd, STAGE_EXECVE);
}

// 报告后仍不能退出：PID 1 一退出，父进程可能先看到 init 丢失而不是退出报告。
// 这里一直回收孤儿，直到父进程杀死整组。没有子进程时 namespace 里也不会再出现孤儿，
// 于是挂起等待；用 sigsuspend 而不是 pause，因为 pause 不在过滤器的允许清单里。
static _Noreturn void reap_forever(void) {
    sigset_t none;
    sigemptyset(&none);
    for (;;) {
        if (wait(NULL) < 0 && errno == ECHILD)
            sigsuspend(&none);
    }
}

// 父进程消失时让整个 namespace 随之终止；下一次使用这个 box 时会清理残留的执行组。
// 内核在凭据变化时会清掉 PDEATHSIG，所以降权之后必须重新设置；设置之前父进程可能已经死了，
// 这时 report 写端已没有读者（init 自己的读端早已关闭），poll 报 POLLERR，直接退出。
static void die_with_parent(int phase) {
    TRY(phase, prctl(PR_SET_PDEATHSIG, SIGKILL, 0, 0, 0));
    struct pollfd p = {.fd = report_fd, .events = 0};
    if (poll(&p, 1, 0) > 0 && (p.revents & POLLERR))
        _exit(FAILURE_EXIT_CODE);
}

_Noreturn void init_main(const struct child_plan *plan) {
    report_fd = plan->report_w;
    close(plan->report_r);
    die_with_parent(PHASE_ROOTFS);
    prepare_root(plan);
    int slot = plan->box_index;
    uid_t payload_uid = plan->config->payload_uid + (uid_t)slot;
    gid_t payload_gid = plan->config->payload_gid + (gid_t)slot;
    int stdin_fd;
    TRY(PHASE_INPUTS, load_inputs(plan, payload_uid, payload_gid, &stdin_fd));
    char path[PATH_MAX];
    TRY(PHASE_COMMAND, resolve_command(plan->spec->args[0], path, sizeof path));
    pid_t child = fork();
    if (child < 0)
        init_fail(PHASE_FORK);
    if (child == 0)
        payload_main(plan, path, stdin_fd);
    // init 自己不需要这些句柄；尤其 exec_error 写端，留着会让父进程永远等不到 EOF。
    close(plan->exec_error_w);
    close(plan->stdout_w);
    close(plan->stderr_w);
    close(stdin_fd);
    close(plan->box->dir);
    close(plan->box->in);
    close(plan->box->out);
    close(plan->box->stdin_file);
    close(plan->workspace);
    TRY(PHASE_SUPERVISOR,
        drop_privileges(plan->config->init_uid + (uid_t)slot, plan->config->init_gid + (gid_t)slot));
    die_with_parent(PHASE_SUPERVISOR);
    TRY(PHASE_SUPERVISOR, filter_install(FILTER_SUPERVISOR));
    int status;
    for (;;) {
        pid_t w = waitpid(child, &status, 0);
        if (w == child)
            break;
        if (w < 0 && errno != EINTR)
            init_fail(PHASE_WAIT);
    }
    struct report r = {.kind = REPORT_EXIT};
    if (WIFEXITED(status))
        r.a = WEXITSTATUS(status);
    else if (WIFSIGNALED(status)) {
        r.a = -1;
        r.b = WTERMSIG(status);
    }
    (void)write_all(report_fd, &r, sizeof r);
    reap_forever();
}
