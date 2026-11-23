// sandbox：一次性隔离执行器，setuid-root 安装，只有 judge 服务所在的组能执行。
//
//   sandbox --box N
//
// 进程结构（没有握手，进程之间只有 CLOEXEC 错误管道、退出报告管道与退出码）：
//
//   sandbox（宿主侧，root）   校验配置与请求 → 锁定 box → 建执行组 → clone(6 个 namespace)
//     └─ init（PID 1）        挂载 rootfs/work/proc/dev → pivot_root → 复制输入 → fork → 降权
//          └─ 用户程序        rlimit → 降到 payload 身份 → seccomp → execve
//   sandbox 监督墙钟、CPU、输出与取消 → 整组 kill → 最终计量 → 取回产物 → 输出一行 JSON
//
// 退出码：0 事实已输出且执行组已回收（其中可能带平台错误）；1 拒绝执行，什么都没启动；
// 2 回收未确认，调用方必须停止接单。
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/capability.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#include "sandbox.h"

static _Noreturn void refuse(int code, const char *message) {
    fprintf(stderr, "sandbox: %s\n", message);
    exit(code);
}

static void disable_coredumps(void) {
    struct rlimit inherited;
    if (getrlimit(RLIMIT_CORE, &inherited) != 0)
        refuse(EXIT_REFUSED, "cannot read RLIMIT_CORE");
    if (inherited.rlim_max < 1)
        refuse(EXIT_REFUSED, "RLIMIT_CORE hard limit must be at least 1 to disable piped core dumps");
    // 0 不能禁止管道式 core 收集；1 是 Linux 阻止收集器递归崩溃的特殊值。
    // 在 fork 前固定软、硬上限，init、用户程序与后代均继承它。
    const struct rlimit disabled = {1, 1};
    if (setrlimit(RLIMIT_CORE, &disabled) != 0)
        refuse(EXIT_REFUSED, "cannot set RLIMIT_CORE to 1 to disable piped core dumps");
}

// setuid 程序的调用方控制着环境变量、打开的 FD 与 umask，这些都不能带进后续步骤。
static void harden(void) {
    umask(077);
    for (int fd = 0; fd <= 2; fd++)
        if (fcntl(fd, F_GETFD) < 0 && open("/dev/null", O_RDWR) != fd)
            _exit(EXIT_REFUSED);
    if (syscall(SYS_close_range, 3, ~0U, 0) != 0)
        _exit(EXIT_REFUSED);
    if (clearenv() != 0 || prctl(PR_SET_DUMPABLE, 0, 0, 0, 0) != 0)
        _exit(EXIT_REFUSED);
    disable_coredumps();
}

static int parse_box(int argc, char **argv) {
    if (argc != 3 || strcmp(argv[1], "--box") != 0)
        refuse(EXIT_REFUSED, "usage: sandbox --box N");
    char *end;
    long n = strtol(argv[2], &end, 10);
    if (*argv[2] == 0 || *end || n < 0 || n >= MAX_BOX_COUNT)
        refuse(EXIT_REFUSED, "box must be a non-negative integer below the limit");
    return (int)n;
}

// 调用方只能是配置中的服务身份（或 root）。确认后切到完整的 root 身份，后续步骤不再受调用方影响。
static void become_root(const struct config *c) {
    if (geteuid() != 0)
        refuse(EXIT_REFUSED, "not installed setuid root");
    uid_t caller = getuid();
    if (caller != 0 && caller != c->service_uid)
        refuse(EXIT_REFUSED, "caller is not the sandbox service");
    if (setresgid(0, 0, 0) != 0 || setresuid(0, 0, 0) != 0)
        refuse(EXIT_REFUSED, "cannot assume root identity");
    // 执行器意外死亡时，init 靠 PDEATHSIG 带着整个 namespace 退出。内核按普通 kill 的权限
    // 投递这个信号：发送方是 root、目标是 init 身份，没有 CAP_KILL 信号会被静默丢弃，
    // 执行组就会在无人监督的情况下继续运行。所以边界集里没有 CAP_KILL 时拒绝执行。
    if (prctl(PR_CAPBSET_READ, CAP_KILL, 0, 0, 0) != 1)
        refuse(EXIT_REFUSED, "CAP_KILL is required to reclaim the namespace if the executor dies");
}

int main(int argc, char **argv) {
    harden();
    int index = parse_box(argc, argv);
    char err[512];
    struct config config;
    if (config_load(SANDBOX_CONFIG, &config, err, sizeof err) != 0)
        refuse(EXIT_REFUSED, err);
    become_root(&config);
    if (index >= config.box_count)
        refuse(EXIT_REFUSED, "box index is outside the configured range");
    struct box box;
    char *data;
    size_t len;
    struct spec spec;
    if (box_open(&config, index, &box, err, sizeof err) != 0 ||
        box_read_spec(&box, &config, &data, &len, err, sizeof err) != 0 ||
        spec_parse(data, len, &spec, err, sizeof err) != 0 ||
        toolchain_rootfs(&config, spec.toolchain, spec.rootfs, sizeof spec.rootfs, err, sizeof err) != 0)
        refuse(EXIT_REFUSED, err);

    struct job_cgroup cgroup;
    int rc = cgroup_create(&config, index, &spec.limits, &cgroup, err, sizeof err);
    if (rc == -2)
        refuse(EXIT_UNRECLAIMED, err);
    if (rc != 0) {
        if (cgroup_remove(&cgroup) != 0)
            refuse(EXIT_UNRECLAIMED, err);
        refuse(EXIT_REFUSED, err);
    }

    struct run run;
    execute(&config, &spec, &box, &cgroup, &run);
    if (!run.reclaimed)
        refuse(EXIT_UNRECLAIMED, "execution group reclaim was not confirmed");
    struct conclusion conclusion;
    conclude(&run.facts, &conclusion);
    struct artifact found[MAX_OUTPUTS];
    int nfound = 0;
    // 平台故障时不交付产物：这次执行的事实不可信。stdout/stderr 已有界，照常写回供诊断。
    if (box_write_file(&box, &config, "stdout", run.stdout_data, run.stdout_len) != 0 ||
        box_write_file(&box, &config, "stderr", run.stderr_data, run.stderr_len) != 0) {
        conclusion.reason = REASON_PLATFORM;
        snprintf(conclusion.failure, sizeof conclusion.failure, "write output streams: %s", strerror(errno));
    }
    if (conclusion.reason != REASON_PLATFORM && run.workspace >= 0 &&
        box_collect_artifacts(&box, &config, &spec, run.workspace, found, &nfound, err, sizeof err) != 0) {
        conclusion.reason = REASON_PLATFORM;
        snprintf(conclusion.failure, sizeof conclusion.failure, "%s", err);
        nfound = 0;
    }
    if (run.workspace >= 0)
        close(run.workspace);
    if (cgroup_remove(&cgroup) != 0)
        refuse(EXIT_UNRECLAIMED, "remove execution group");
    write_facts(&run, &conclusion, &spec, found, nfound);
    return EXIT_FACTS;
}
