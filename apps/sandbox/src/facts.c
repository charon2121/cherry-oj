// 从事实推出结论。这里不做 I/O、不改状态，规则与原 Go 版 isolator 的 conclude 一致。
#define _GNU_SOURCE
#include <signal.h>
#include <stdio.h>
#include <string.h>

#include "sandbox.h"

const char *reason_name(enum reason r) {
    switch (r) {
    case REASON_CPU:
        return "cpu";
    case REASON_WALL:
        return "wall";
    case REASON_OUTPUT:
        return "output";
    case REASON_CANCELLED:
        return "cancelled";
    case REASON_PLATFORM:
        return "platform";
    default:
        return "";
    }
}

static void fail(struct conclusion *c, const char *message) {
    size_t used = strlen(c->failure);
    snprintf(c->failure + used, sizeof c->failure - used, "%s%s", used ? "; " : "", message);
    c->reason = REASON_PLATFORM;
}

void conclude(const struct facts *f, struct conclusion *c) {
    memset(c, 0, sizeof *c);
    c->reason = f->supervision.reason;
    c->signal = f->supervision.signal;
    snprintf(c->failure, sizeof c->failure, "%s", f->supervision.failure);
    // 取消是调用方主动放弃，不是超了预算；已有更具体的原因时不覆盖。
    if (f->cancelled && c->reason == REASON_NONE)
        c->reason = REASON_CANCELLED;
    if (f->usage_valid) {
        // oom_kill 只说明组里有受害进程，也可能来自祖先或全局 OOM；
        // 没有本任务 oom 证据时，不能把平台内存压力归为用户命令超限。
        if (f->usage.oom_kill > 0 && f->usage.oom == 0)
            fail(c, "OOM victim without task-local OOM evidence");
        // 本任务 OOM 可能连 init 一起杀死，最终计量正好解释了丢失的退出报告。
        if (f->supervision.report_lost && f->usage.oom > 0 && f->usage.oom_kill > 0) {
            if (c->reason == REASON_NONE)
                c->signal = SIGKILL;
        } else if (f->supervision.report_lost) {
            fail(c, "init exited before reporting the payload exit");
        }
        // 采样可能恰好错过最后一段 CPU，用最终计量兜底。
        if (c->reason == REASON_NONE && f->cpu_ns >= f->cpu_budget)
            c->reason = REASON_CPU;
    } else {
        fail(c, "execution group reclaim was not confirmed");
    }
    if (f->output_exceeded && c->reason == REASON_NONE)
        c->reason = REASON_OUTPUT;
}
