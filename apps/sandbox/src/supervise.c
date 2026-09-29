// 父进程：建工作区与管道，clone 出 init，然后监督到这次执行停下来，停组，取最终计量。
//
// 监督循环等第一个「该停了」的事件：
//   用户程序退出（init 报告）、init 丢失、execve 失败、CPU 用完、墙钟用完、
//   stdout/stderr 超限、调用方取消、启动超时。
// 墙钟与 CPU 都从 execve 成功（exec_error 管道 EOF）起算；CPU 为执行组计量减去当时的快照。
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/mount.h>
#include <linux/sched.h>
#include <poll.h>
#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/timerfd.h>
#include <sys/wait.h>
#include <unistd.h>

#include "sandbox.h"

// 用新挂载 API 建一块不挂在宿主任何路径上的 tmpfs：父进程持有它的 FD 读取产物，
// init 在自己的 mount namespace 里把它接到 /work。本进程退出后它随最后一个 FD 消失。
static int make_workspace(uid_t uid, gid_t gid) {
    int fs = (int)syscall(SYS_fsopen, "tmpfs", FSOPEN_CLOEXEC);
    if (fs < 0)
        return -1;
    char size[32], inodes[32], owner[16], group[16];
    snprintf(size, sizeof size, "%lld", (long long)WORKSPACE_BYTES);
    snprintf(inodes, sizeof inodes, "%d", WORKSPACE_INODES);
    snprintf(owner, sizeof owner, "%u", uid);
    snprintf(group, sizeof group, "%u", gid);
    const char *options[][2] = {{"size", size}, {"nr_inodes", inodes}, {"mode", "0755"}, {"uid", owner}, {"gid", group}};
    for (size_t i = 0; i < sizeof options / sizeof options[0]; i++)
        if (syscall(SYS_fsconfig, fs, FSCONFIG_SET_STRING, options[i][0], options[i][1], 0) != 0)
            goto fail;
    if (syscall(SYS_fsconfig, fs, FSCONFIG_CMD_CREATE, NULL, NULL, 0) != 0)
        goto fail;
    int mnt = (int)syscall(SYS_fsmount, fs, FSMOUNT_CLOEXEC, MOUNT_ATTR_NOSUID | MOUNT_ATTR_NODEV);
    close(fs);
    return mnt;
fail:
    close(fs);
    return -1;
}

struct stream {
    int fd;
    char *data;
    size_t len, limit;
    bool exceeded;
};

// 读到上限为止；多出来的字节只用来判定超限，不保存。返回 0 表示 EOF。
static int drain(struct stream *s) {
    char buf[64 << 10];
    for (;;) {
        ssize_t n = read(s->fd, buf, sizeof buf);
        if (n < 0 && errno == EINTR)
            continue;
        if (n < 0)
            return errno == EAGAIN ? 1 : -1;
        if (n == 0) {
            close(s->fd);
            s->fd = -1;
            return 0;
        }
        size_t take = (size_t)n;
        if (s->len + take > s->limit) {
            take = s->limit - s->len;
            s->exceeded = true;
        }
        memcpy(s->data + s->len, buf, take);
        s->len += take;
    }
}

static void fail(struct supervision *s, const char *fmt, ...) __attribute__((format(printf, 2, 3)));
static void fail(struct supervision *s, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    if (!s->failure[0])
        vsnprintf(s->failure, sizeof s->failure, fmt, ap);
    va_end(ap);
    s->reason = REASON_PLATFORM;
}

struct monitor {
    struct supervision *sup;
    const struct jobgroup *group;
    const struct limits *limits;
    struct stream out, err;
    int exec_error, report, pidfd, cancel, timer;
    int64_t cloned_at, started_at, cpu_base;
};

// exec_error 管道：有数据是 execve 前的失败记录；EOF 且无数据表示 execve 成功。
static void on_exec_error(struct monitor *m) {
    struct exec_failure f;
    ssize_t n = read(m->exec_error, &f, sizeof f);
    if (n < 0 && (errno == EAGAIN || errno == EINTR))
        return;
    close(m->exec_error);
    m->exec_error = -1;
    if (n == 0) {
        struct usage u;
        m->sup->started = true;
        m->started_at = monotonic_ns();
        if (group_usage(m->group, &u) != 0)
            fail(m->sup, "read CPU baseline: %s", strerror(errno));
        else
            m->cpu_base = u.cpu_ns;
        return;
    }
    if (n == (ssize_t)sizeof f)
        fail(m->sup, "payload exec failed to start: stage=%u errno=%d", f.stage, f.err);
    else
        fail(m->sup, "payload exec failed with a truncated record");
}

static bool on_report(struct monitor *m) {
    struct report r;
    ssize_t n = read(m->report, &r, sizeof r);
    if (n < 0 && (errno == EAGAIN || errno == EINTR))
        return false;
    close(m->report);
    m->report = -1;
    if (n == 0)
        return false; // init 在报告前消失；由 pidfd 事件判定
    if (n != (ssize_t)sizeof r) {
        fail(m->sup, "truncated init report");
        return true;
    }
    if (r.kind == REPORT_INIT_FAILED) {
        fail(m->sup, "init failed: phase=%d errno=%d", r.a, r.b);
        return true;
    }
    // 用户程序已退出：exec_error 管道此时一定已有结论（数据或 EOF），先把它读掉。
    if (m->exec_error >= 0)
        on_exec_error(m);
    m->sup->exited = true;
    m->sup->exit_code = r.a;
    m->sup->signal = r.b;
    return true;
}

// 取不到快照时按平台故障停止：拿不准预算就继续跑，等于让已超时的命令继续占着名额。
static bool on_tick(struct monitor *m) {
    uint64_t expirations;
    // 只关心「到点了」，到期次数不影响判断；读失败（EAGAIN）下一轮仍会触发。
    if (read(m->timer, &expirations, sizeof expirations) < 0 && errno != EAGAIN)
        return false;
    int64_t now = monotonic_ns();
    if (!m->sup->started) {
        if (now - m->cloned_at > STARTUP_TIMEOUT_NS) {
            fail(m->sup, "isolated startup timed out");
            return true;
        }
        return false;
    }
    if (now - m->started_at >= m->limits->clock_ns) {
        m->sup->reason = REASON_WALL;
        return true;
    }
    struct usage u;
    if (group_usage(m->group, &u) != 0) {
        fail(m->sup, "sample CPU: %s", strerror(errno));
        return true;
    }
    if (u.cpu_ns - m->cpu_base >= m->limits->cpu_ns) {
        m->sup->reason = REASON_CPU;
        return true;
    }
    return false;
}

static void supervise(struct monitor *m, bool *cancelled) {
    for (;;) {
        struct pollfd p[7];
        int n = 0;
        int slot_out = -1, slot_err = -1, slot_exec = -1, slot_report = -1, slot_pid = -1, slot_cancel = -1;
        if (m->out.fd >= 0)
            slot_out = n, p[n++] = (struct pollfd){.fd = m->out.fd, .events = POLLIN};
        if (m->err.fd >= 0)
            slot_err = n, p[n++] = (struct pollfd){.fd = m->err.fd, .events = POLLIN};
        if (m->exec_error >= 0)
            slot_exec = n, p[n++] = (struct pollfd){.fd = m->exec_error, .events = POLLIN};
        if (m->report >= 0)
            slot_report = n, p[n++] = (struct pollfd){.fd = m->report, .events = POLLIN};
        slot_pid = n, p[n++] = (struct pollfd){.fd = m->pidfd, .events = POLLIN};
        if (m->cancel >= 0)
            slot_cancel = n, p[n++] = (struct pollfd){.fd = m->cancel, .events = POLLIN};
        int slot_timer = n;
        p[n++] = (struct pollfd){.fd = m->timer, .events = POLLIN};
        if (poll(p, (nfds_t)n, -1) < 0) {
            if (errno == EINTR)
                continue;
            fail(m->sup, "poll: %s", strerror(errno));
            return;
        }
        // 取消优先于其他事件：调用方已经放弃，不能再把这次执行当成有效结果。
        if (slot_cancel >= 0 && p[slot_cancel].revents) {
            *cancelled = true;
            m->sup->reason = REASON_CANCELLED;
            return;
        }
        struct stream *streams[] = {&m->out, &m->err};
        int slots[] = {slot_out, slot_err};
        for (int i = 0; i < 2; i++) {
            if (slots[i] < 0 || !p[slots[i]].revents)
                continue;
            if (drain(streams[i]) < 0) {
                fail(m->sup, "read output: %s", strerror(errno));
                return;
            }
            if (streams[i]->exceeded) {
                m->sup->reason = REASON_OUTPUT;
                return;
            }
        }
        if (slot_exec >= 0 && p[slot_exec].revents) {
            on_exec_error(m);
            if (m->sup->reason == REASON_PLATFORM)
                return;
        }
        if (slot_report >= 0 && p[slot_report].revents && on_report(m))
            return;
        if (p[slot_pid].revents && !m->sup->exited) {
            // init 在报告用户程序退出前就没了。可能是本任务 OOM 连 init 一起杀死，要等最终计量判定。
            m->sup->report_lost = true;
            return;
        }
        if (p[slot_timer].revents && on_tick(m))
            return;
    }
}

static int set_nonblock(int fd) {
    int flags = fcntl(fd, F_GETFL);
    return flags < 0 ? -1 : fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}

// 调用方把一根管道接在本进程的 stdin 上：有数据或被关闭都表示取消，调用方崩溃时同样生效。
// stdin 不是管道时（例如人工调用）不监听取消。
static int cancel_fd(void) {
    struct stat st;
    return fstat(0, &st) == 0 && S_ISFIFO(st.st_mode) ? 0 : -1;
}

int execute(const struct config *c, const struct spec *s, const struct box *b, struct jobgroup *g, struct run *r) {
    memset(r, 0, sizeof *r);
    r->workspace = -1;
    struct facts *f = &r->facts;
    f->cpu_budget = s->limits.cpu_ns;
    r->stdout_data = malloc((size_t)s->limits.stdout_max_bytes + 1);
    r->stderr_data = malloc((size_t)s->limits.stderr_max_bytes + 1);
    int slot = b->index;
    int workspace = make_workspace(c->payload_uid + (uid_t)slot, c->payload_gid + (gid_t)slot);
    int out[2] = {-1, -1}, err[2] = {-1, -1}, exec_error[2] = {-1, -1}, report[2] = {-1, -1};
    int timer = timerfd_create(CLOCK_MONOTONIC, TFD_CLOEXEC | TFD_NONBLOCK);
    if (!r->stdout_data || !r->stderr_data || workspace < 0 || timer < 0 || pipe2(out, O_CLOEXEC) ||
        pipe2(err, O_CLOEXEC) || pipe2(exec_error, O_CLOEXEC) || pipe2(report, O_CLOEXEC)) {
        fail(&f->supervision, "prepare execution: %s", strerror(errno));
        r->reclaimed = true; // 什么都还没启动，没有需要回收的进程
        return 0;
    }
    struct child_plan plan = {.config = c, .spec = s, .box = b, .box_index = slot, .workspace = workspace,
                              .stdout_w = out[1], .stderr_w = err[1], .exec_error_w = exec_error[1],
                              .report_w = report[1], .report_r = report[0]};
    int pidfd = -1;
    struct clone_args args = {
        .flags = CLONE_NEWNS | CLONE_NEWPID | CLONE_NEWNET | CLONE_NEWIPC | CLONE_NEWUTS | CLONE_NEWCGROUP |
                 CLONE_INTO_CGROUP | CLONE_PIDFD,
        .pidfd = (uint64_t)(uintptr_t)&pidfd,
        .exit_signal = SIGCHLD,
        .cgroup = (uint64_t)g->dir,
    };
    int64_t cloned_at = monotonic_ns();
    long pid = syscall(SYS_clone3, &args, sizeof args);
    if (pid == 0)
        init_main(&plan);
    close(out[1]);
    close(err[1]);
    close(exec_error[1]);
    close(report[1]);
    if (pid < 0) {
        fail(&f->supervision, "clone isolated init: %s", strerror(errno));
        r->reclaimed = true;
        return 0;
    }
    struct itimerspec tick = {.it_interval = {0, CPU_SAMPLE_NS}, .it_value = {0, CPU_SAMPLE_NS}};
    struct monitor m = {
        .sup = &f->supervision, .group = g, .limits = &s->limits,
        .out = {.fd = out[0], .data = r->stdout_data, .limit = (size_t)s->limits.stdout_max_bytes},
        .err = {.fd = err[0], .data = r->stderr_data, .limit = (size_t)s->limits.stderr_max_bytes},
        .exec_error = exec_error[0], .report = report[0], .pidfd = pidfd, .cancel = cancel_fd(), .timer = timer,
        .cloned_at = cloned_at,
    };
    if (set_nonblock(out[0]) || set_nonblock(err[0]) || set_nonblock(exec_error[0]) || set_nonblock(report[0]) ||
        timerfd_settime(timer, 0, &tick, NULL))
        fail(&f->supervision, "prepare supervision: %s", strerror(errno));
    else
        supervise(&m, &f->cancelled);
    int64_t stopped_at = monotonic_ns();

    // 停组并确认清空之后，读到的计量才是最终事实；确认不了就不能交付，也不能复用这个 box。
    if (group_stop(g, &f->usage) != 0) {
        r->reclaimed = false;
        return 0;
    }
    r->reclaimed = true;
    f->usage_valid = true;
    siginfo_t info;
    (void)waitid((idtype_t)P_PIDFD, (id_t)pidfd, &info, WEXITED);
    // 所有写端都已随整组消失，剩余输出读到 EOF 为止；超出上限同样记为超限。
    for (struct stream *st = &m.out; st <= &m.err; st++) {
        while (st->fd >= 0 && drain(st) > 0) {
        }
        if (st->fd >= 0)
            close(st->fd);
    }
    f->output_exceeded = m.out.exceeded || m.err.exceeded;
    r->stdout_len = m.out.len;
    r->stderr_len = m.err.len;
    f->clock_ns = f->supervision.started ? stopped_at - m.started_at : 0;
    f->cpu_ns = f->usage.cpu_ns - m.cpu_base;
    // 工作区 FD 留给产物收集；这里关闭其余句柄。
    int leftovers[] = {m.exec_error, m.report, pidfd, timer};
    for (size_t i = 0; i < sizeof leftovers / sizeof leftovers[0]; i++)
        if (leftovers[i] >= 0)
            close(leftovers[i]);
    r->workspace = workspace;
    return 0;
}
