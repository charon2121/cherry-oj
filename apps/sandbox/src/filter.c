// seccomp 策略：允许清单外的系统调用一律杀死进程。
//
// 允许普通 I/O、动态链接、进程/线程、信号与时钟；网络、提权、挂载与内核管理不在表中。
// 用户程序（PAYLOAD）与 init（SUPERVISOR）的差别只在于 init 不能再 execve。
// 编译器与普通命令目前使用同一份策略，它们的宿主安全边界相同。
#define _GNU_SOURCE
#include <errno.h>
#include <seccomp.h>
#include <signal.h>
#include <stddef.h>
#include <sys/prctl.h>

#include "sandbox.h"

static const int allowed[] = {
    SCMP_SYS(read), SCMP_SYS(write), SCMP_SYS(open), SCMP_SYS(close), SCMP_SYS(stat), SCMP_SYS(fstat),
    SCMP_SYS(lstat), SCMP_SYS(poll), SCMP_SYS(lseek), SCMP_SYS(mmap), SCMP_SYS(mprotect), SCMP_SYS(munmap),
    SCMP_SYS(brk), SCMP_SYS(rt_sigaction), SCMP_SYS(rt_sigprocmask), SCMP_SYS(rt_sigreturn), SCMP_SYS(pread64),
    SCMP_SYS(pwrite64), SCMP_SYS(readv), SCMP_SYS(writev), SCMP_SYS(access), SCMP_SYS(pipe), SCMP_SYS(select),
    SCMP_SYS(sched_yield), SCMP_SYS(mremap), SCMP_SYS(msync), SCMP_SYS(mincore), SCMP_SYS(madvise), SCMP_SYS(dup),
    SCMP_SYS(dup2), SCMP_SYS(nanosleep), SCMP_SYS(getitimer), SCMP_SYS(alarm), SCMP_SYS(setitimer), SCMP_SYS(getpid),
    SCMP_SYS(fork), SCMP_SYS(vfork), SCMP_SYS(exit), SCMP_SYS(wait4), SCMP_SYS(kill), SCMP_SYS(uname),
    SCMP_SYS(fcntl), SCMP_SYS(flock), SCMP_SYS(fsync), SCMP_SYS(fdatasync), SCMP_SYS(truncate), SCMP_SYS(ftruncate),
    SCMP_SYS(getdents), SCMP_SYS(getcwd), SCMP_SYS(chdir), SCMP_SYS(fchdir), SCMP_SYS(rename), SCMP_SYS(mkdir),
    SCMP_SYS(rmdir), SCMP_SYS(creat), SCMP_SYS(unlink), SCMP_SYS(readlink), SCMP_SYS(chmod), SCMP_SYS(fchmod),
    SCMP_SYS(umask), SCMP_SYS(gettimeofday), SCMP_SYS(getrlimit), SCMP_SYS(getrusage), SCMP_SYS(sysinfo),
    SCMP_SYS(times), SCMP_SYS(getuid), SCMP_SYS(getgid), SCMP_SYS(geteuid), SCMP_SYS(getegid), SCMP_SYS(getppid),
    SCMP_SYS(getpgrp), SCMP_SYS(setsid), SCMP_SYS(getgroups), SCMP_SYS(getresuid), SCMP_SYS(getresgid),
    SCMP_SYS(getpgid), SCMP_SYS(getsid), SCMP_SYS(capget), SCMP_SYS(rt_sigpending), SCMP_SYS(rt_sigtimedwait),
    SCMP_SYS(rt_sigqueueinfo), SCMP_SYS(rt_sigsuspend), SCMP_SYS(sigaltstack), SCMP_SYS(arch_prctl),
    SCMP_SYS(setrlimit), SCMP_SYS(gettid), SCMP_SYS(tkill), SCMP_SYS(time), SCMP_SYS(futex),
    SCMP_SYS(sched_getaffinity), SCMP_SYS(getdents64), SCMP_SYS(set_tid_address), SCMP_SYS(restart_syscall),
    SCMP_SYS(fadvise64), SCMP_SYS(clock_gettime), SCMP_SYS(clock_getres), SCMP_SYS(clock_nanosleep),
    SCMP_SYS(exit_group), SCMP_SYS(epoll_wait), SCMP_SYS(epoll_ctl), SCMP_SYS(tgkill), SCMP_SYS(utimes),
    SCMP_SYS(waitid), SCMP_SYS(openat), SCMP_SYS(mkdirat), SCMP_SYS(newfstatat), SCMP_SYS(unlinkat),
    SCMP_SYS(renameat), SCMP_SYS(readlinkat), SCMP_SYS(fchmodat), SCMP_SYS(faccessat), SCMP_SYS(pselect6),
    SCMP_SYS(ppoll), SCMP_SYS(set_robust_list), SCMP_SYS(get_robust_list), SCMP_SYS(utimensat),
    SCMP_SYS(epoll_pwait), SCMP_SYS(timerfd_create), SCMP_SYS(timerfd_settime), SCMP_SYS(timerfd_gettime),
    SCMP_SYS(signalfd4), SCMP_SYS(eventfd2), SCMP_SYS(epoll_create1), SCMP_SYS(dup3), SCMP_SYS(pipe2),
    SCMP_SYS(prlimit64), SCMP_SYS(getcpu), SCMP_SYS(getrandom), SCMP_SYS(membarrier), SCMP_SYS(statx),
    SCMP_SYS(rseq), SCMP_SYS(close_range), SCMP_SYS(faccessat2), SCMP_SYS(epoll_pwait2), SCMP_SYS(fchmodat2),
};

// 只允许进程名称、匿名 VMA 名称与权限状态查询；不允许修改安全状态。
// 3 PR_GET_DUMPABLE、15 PR_SET_NAME、16 PR_GET_NAME、21 PR_GET_SECCOMP、23 PR_CAPBSET_READ、
// 39 PR_GET_NO_NEW_PRIVS、0x53564d41 PR_SET_VMA。
static const uint64_t allowed_prctl[] = {3, 15, 16, 21, 23, 39, 0x53564d41};

// clone 只允许普通 fork/线程需要的 flags；namespace、ptrace、CLONE_PARENT 等一律拒绝。
// 低 8 位是退出信号，只允许 0 或 SIGCHLD。
static const uint64_t allowed_clone = 0x100 | 0x200 | 0x400 | 0x800 | 0x4000 | 0x10000 | 0x40000 | 0x80000 |
                                      0x100000 | 0x200000 | 0x1000000;

int filter_install(enum filter_profile profile) {
    scmp_filter_ctx ctx = seccomp_init(SCMP_ACT_KILL_PROCESS);
    if (!ctx)
        return -1;
    int rc = 0;
    // 非 x86_64 原生 ABI（含 x32）的调用按错误架构处理，直接杀死。
    rc |= seccomp_attr_set(ctx, SCMP_FLTATR_ACT_BADARCH, SCMP_ACT_KILL_PROCESS);
    for (size_t i = 0; i < sizeof allowed / sizeof allowed[0]; i++)
        rc |= seccomp_rule_add(ctx, SCMP_ACT_ALLOW, allowed[i], 0);
    if (profile == FILTER_PAYLOAD)
        rc |= seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(execve), 0);
    // clone3 的参数是指针结构，classic BPF 无法安全检查；返回 ENOSYS 让 libc 回退到受检查的 clone。
    rc |= seccomp_rule_add(ctx, SCMP_ACT_ERRNO(ENOSYS), SCMP_SYS(clone3), 0);
    // pidfd_open 是部分运行时的可选探测；返回 ENOSYS 让它们使用既有的 wait/kill 路径。
    rc |= seccomp_rule_add(ctx, SCMP_ACT_ERRNO(ENOSYS), SCMP_SYS(pidfd_open), 0);
    // isatty 不应导致程序被杀；所有 ioctl 返回 ENOTTY，不向设备透传。
    rc |= seccomp_rule_add(ctx, SCMP_ACT_ERRNO(ENOTTY), SCMP_SYS(ioctl), 0);
    for (size_t i = 0; i < sizeof allowed_prctl / sizeof allowed_prctl[0]; i++)
        rc |= seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(prctl), 1, SCMP_A0(SCMP_CMP_EQ, allowed_prctl[i]));
    rc |= seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(clone), 1, SCMP_A0(SCMP_CMP_MASKED_EQ, ~allowed_clone, 0));
    rc |= seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(clone), 1,
                           SCMP_A0(SCMP_CMP_MASKED_EQ, ~allowed_clone, (uint64_t)SIGCHLD));
    if (rc == 0)
        rc = seccomp_load(ctx);
    seccomp_release(ctx);
    return rc == 0 ? 0 : -1;
}
