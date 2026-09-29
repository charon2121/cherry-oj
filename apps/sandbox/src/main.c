// sandbox：一次性隔离执行器，setuid-root 安装，只有 sandbox HTTP 服务的组能执行。
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
#include <linux/openat2.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#include "sandbox.h"

static _Noreturn void refuse(int code, const char *message) {
    fprintf(stderr, "sandbox: %s\n", message);
    exit(code);
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
}

static int parse_box(int argc, char **argv) {
    if (argc != 3 || strcmp(argv[1], "--box") != 0)
        refuse(EXIT_REFUSED, "usage: sandbox --box N");
    char *end;
    long n = strtol(argv[2], &end, 10);
    if (*argv[2] == 0 || *end || n < 0 || n > 3)
        refuse(EXIT_REFUSED, "box must be an integer 0..3");
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
}

static void json_string(FILE *f, const char *s) {
    fputc('"', f);
    for (; *s; s++) {
        unsigned char ch = (unsigned char)*s;
        if (ch == '"' || ch == '\\')
            fprintf(f, "\\%c", ch);
        else if (ch < 0x20 || ch >= 0x7f)
            fprintf(f, "\\u%04x", ch);
        else
            fputc(ch, f);
    }
    fputc('"', f);
}

struct artifact {
    int index;
    int64_t size;
};

// 产物只在执行组清空之后收集：用户进程不能边写边交付。路径限定在工作区内，
// 禁止链接与跨挂载；只接受单链接的普通文件。声明了却没生成的产物跳过，由调用方判断是否要紧。
static int collect(const struct box *b, const struct config *c, const struct spec *s, int workspace,
                   struct artifact *found, int *nfound, char *err, size_t errlen) {
    int64_t total = 0;
    *nfound = 0;
    for (int i = 0; i < s->noutputs; i++) {
        struct open_how how = {.flags = O_RDONLY | O_CLOEXEC | O_NONBLOCK | O_NOFOLLOW,
                               .resolve = RESOLVE_BENEATH | RESOLVE_NO_SYMLINKS | RESOLVE_NO_MAGICLINKS |
                                          RESOLVE_NO_XDEV};
        int fd = (int)syscall(SYS_openat2, workspace, s->outputs[i], &how, sizeof how);
        if (fd < 0 && errno == ENOENT)
            continue;
        struct stat st;
        if (fd < 0 || fstat(fd, &st) != 0 || !S_ISREG(st.st_mode) || st.st_nlink != 1 ||
            st.st_size > MAX_ARTIFACT_BYTES - total) {
            if (fd >= 0)
                close(fd);
            set_error(err, errlen, "artifact %s is not a bounded regular file with exactly one link", s->outputs[i]);
            return -1;
        }
        char name[32];
        snprintf(name, sizeof name, "artifact-%d", i);
        int rc = box_copy_artifact(b, c, name, fd, st.st_size);
        close(fd);
        if (rc != 0) {
            set_error(err, errlen, "deliver artifact %s: %s", s->outputs[i], strerror(errno));
            return -1;
        }
        total += st.st_size;
        found[(*nfound)++] = (struct artifact){i, st.st_size};
    }
    return 0;
}

static void print_facts(const struct run *r, const struct conclusion *c, const struct spec *s,
                        const struct artifact *found, int nfound) {
    const struct facts *f = &r->facts;
    int exit_code = f->supervision.exit_code, signal = c->signal;
    if (signal)
        exit_code = -1;
    printf("{\"version\":1,\"exitCode\":%d,\"signal\":%d,\"cpuNs\":%lld,\"memoryBytes\":%lld,\"clockNs\":%lld,",
           exit_code, signal, (long long)f->cpu_ns, (long long)f->usage.memory_bytes, (long long)f->clock_ns);
    printf("\"reason\":\"%s\",\"oom\":%llu,\"oomKill\":%llu,\"memoryMaxEvents\":%llu,\"pidsMaxEvents\":%llu,",
           reason_name(c->reason), (unsigned long long)f->usage.oom, (unsigned long long)f->usage.oom_kill,
           (unsigned long long)f->usage.memory_max_events, (unsigned long long)f->usage.pids_max_events);
    printf("\"cancelled\":%s,\"outputExceeded\":%s,\"stdoutBytes\":%zu,\"stderrBytes\":%zu,\"outputs\":[",
           f->cancelled ? "true" : "false", f->output_exceeded ? "true" : "false", r->stdout_len, r->stderr_len);
    for (int i = 0; i < nfound; i++) {
        printf("%s{\"index\":%d,\"path\":", i ? "," : "", found[i].index);
        json_string(stdout, s->outputs[found[i].index]);
        printf(",\"sizeBytes\":%lld}", (long long)found[i].size);
    }
    printf("],\"error\":");
    json_string(stdout, c->failure);
    printf("}\n");
    fflush(stdout);
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
        spec_parse(data, len, &spec, err, sizeof err) != 0)
        refuse(EXIT_REFUSED, err);

    struct jobgroup group;
    int rc = group_create(&config, index, &spec.limits, &group, err, sizeof err);
    if (rc == -2)
        refuse(EXIT_UNRECLAIMED, err);
    if (rc != 0) {
        if (group_remove(&group) != 0)
            refuse(EXIT_UNRECLAIMED, err);
        refuse(EXIT_REFUSED, err);
    }

    struct run run;
    execute(&config, &spec, &box, &group, &run);
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
        collect(&box, &config, &spec, run.workspace, found, &nfound, err, sizeof err) != 0) {
        conclusion.reason = REASON_PLATFORM;
        snprintf(conclusion.failure, sizeof conclusion.failure, "%s", err);
        nfound = 0;
    }
    if (run.workspace >= 0)
        close(run.workspace);
    if (group_remove(&group) != 0)
        refuse(EXIT_UNRECLAIMED, "remove execution group");
    print_facts(&run, &conclusion, &spec, found, nfound);
    return EXIT_FACTS;
}
