// 归纳监督与计量事实，按执行器协议输出一行 JSON。
#define _GNU_SOURCE
#include <signal.h>
#include <stdio.h>
#include <string.h>

#include "sandbox.h"

static const char *reason_name(enum reason r) {
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

void write_facts(const struct run *r, const struct conclusion *c, const struct spec *s,
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
        printf(",\"sizeBytes\":%lld}", (long long)found[i].size_bytes);
    }
    printf("],\"error\":");
    json_string(stdout, c->failure);
    printf("}\n");
    fflush(stdout);
}
