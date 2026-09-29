// 执行组：jobs/box-<N>。同一 box 由 flock 保证串行，所以组名可以固定；上次执行若没回收干净，
// 这次先把残留的组杀空并删掉，删不掉就不能继续用这个 box。
//
// jobs 子树由 systemd 委派给 sandbox HTTP 服务，本程序以 root 在其中建组；组内文件属于 root，
// 服务身份不能改写正在执行的组的限额，也不能把进程移进移出。
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <linux/magic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/vfs.h>
#include <time.h>
#include <unistd.h>

#include "sandbox.h"

// 解析 "key value" 行；缺项必须是错误：零是合法的资源事实，不能用缺省值掩盖接口缺失。
static int field(const char *text, const char *key, uint64_t *out) {
    size_t klen = strlen(key);
    for (const char *line = text; line && *line; line = strchr(line, '\n') ? strchr(line, '\n') + 1 : NULL) {
        if (strncmp(line, key, klen) == 0 && line[klen] == ' ') {
            char *end;
            errno = 0;
            unsigned long long v = strtoull(line + klen + 1, &end, 10);
            if (errno || (*end != '\n' && *end != 0))
                return -1;
            *out = v;
            return 0;
        }
    }
    return -1;
}

int group_usage(const struct jobgroup *g, struct usage *u) {
    char cpu[4096], peak[64], memory[4096], pids[256], events[256];
    if (read_file_at(g->dir, "cpu.stat", cpu, sizeof cpu, NULL) || read_file_at(g->dir, "memory.peak", peak, sizeof peak, NULL) ||
        read_file_at(g->dir, "memory.events", memory, sizeof memory, NULL) ||
        read_file_at(g->dir, "pids.events", pids, sizeof pids, NULL) ||
        read_file_at(g->dir, "cgroup.events", events, sizeof events, NULL))
        return -1;
    uint64_t usec, populated;
    char *end;
    errno = 0;
    long long p = strtoll(peak, &end, 10);
    if (field(cpu, "usage_usec", &usec) || usec > INT64_MAX / 1000 || errno || p < 0 || (*end != '\n' && *end) ||
        field(memory, "oom", &u->oom) || field(memory, "oom_kill", &u->oom_kill) ||
        field(memory, "max", &u->memory_max_events) || field(pids, "max", &u->pids_max_events) ||
        field(events, "populated", &populated) || populated > 1)
        return -1;
    u->cpu_ns = (int64_t)usec * 1000;
    u->memory_bytes = p;
    u->populated = populated == 1;
    return 0;
}

static void sleep_ns(int64_t ns) {
    struct timespec ts = {.tv_sec = ns / 1000000000, .tv_nsec = ns % 1000000000};
    while (nanosleep(&ts, &ts) != 0 && errno == EINTR) {
    }
}

// 写 cgroup.kill 杀死整组，再等 populated 变 0。只有确认清空后读到的计量才是最终事实。
static int kill_and_wait(int dir) {
    if (write_file_at(dir, "cgroup.kill", "1") != 0)
        return -1;
    int64_t deadline = monotonic_ns() + RECLAIM_TIMEOUT_NS;
    for (;;) {
        char events[256];
        uint64_t populated;
        if (read_file_at(dir, "cgroup.events", events, sizeof events, NULL) || field(events, "populated", &populated))
            return -1;
        if (populated == 0)
            return 0;
        if (monotonic_ns() > deadline) {
            errno = ETIMEDOUT;
            return -1;
        }
        sleep_ns(CPU_SAMPLE_NS);
    }
}

int group_stop(const struct jobgroup *g, struct usage *final) {
    if (kill_and_wait(g->dir) != 0)
        return -1;
    return group_usage(g, final);
}

int group_remove(struct jobgroup *g) {
    if (g->dir >= 0) {
        close(g->dir);
        g->dir = -1;
    }
    if (g->jobs < 0 || !g->name[0])
        return 0;
    if (unlinkat(g->jobs, g->name, AT_REMOVEDIR) != 0 && errno != ENOENT)
        return -1;
    g->name[0] = 0;
    return 0;
}

// 上次执行的残留组：杀空后删除。失败说明有进程无法回收，本 box 不能再用。
static int remove_stale(int jobs, const char *name) {
    int dir = openat(jobs, name, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (dir < 0)
        return errno == ENOENT ? 0 : -1;
    int rc = kill_and_wait(dir);
    close(dir);
    if (rc == 0 && unlinkat(jobs, name, AT_REMOVEDIR) != 0)
        rc = -1;
    return rc;
}

static bool has_controllers(int dir, const char *file) {
    char buf[512];
    if (read_file_at(dir, file, buf, sizeof buf, NULL))
        return false;
    int found = 0;
    for (char *save = NULL, *w = strtok_r(buf, " \n", &save); w; w = strtok_r(NULL, " \n", &save))
        found += !strcmp(w, "cpu") || !strcmp(w, "memory") || !strcmp(w, "pids");
    return found == 3;
}

// 配置并读回：写进去的值与读回的不一致，说明内核或委派不是我们以为的样子。
static int configure(int dir, const struct limits *l) {
    char memory[32], pids[32];
    snprintf(memory, sizeof memory, "%" PRId64, l->memory_bytes);
    snprintf(pids, sizeof pids, "%" PRId64, l->max_processes);
    // memory.oom.group 让 OOM 杀死整组而不只是某个子进程；cpu.max 把速率限制在一颗 CPU。
    const char *settings[][2] = {{"memory.max", memory}, {"memory.swap.max", "0"}, {"memory.oom.group", "1"},
                                 {"pids.max", pids},     {"cpu.max", "10000 10000"}};
    for (size_t i = 0; i < sizeof settings / sizeof settings[0]; i++) {
        char back[64];
        if (write_file_at(dir, settings[i][0], settings[i][1]) != 0 ||
            read_file_at(dir, settings[i][0], back, sizeof back, NULL) != 0)
            return -1;
        back[strcspn(back, "\n")] = 0;
        if (strcmp(back, settings[i][1]) != 0) {
            errno = EINVAL;
            return -1;
        }
    }
    return 0;
}

int group_create(const struct config *c, int box, const struct limits *l, struct jobgroup *g, char *err, size_t errlen) {
    memset(g, 0, sizeof *g);
    g->dir = -1;
    g->jobs = open(c->cgroup, O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    struct statfs fs;
    if (g->jobs < 0 || fstatfs(g->jobs, &fs) != 0 || fs.f_type != CGROUP2_SUPER_MAGIC ||
        !has_controllers(g->jobs, "cgroup.subtree_control")) {
        set_error(err, errlen, "cgroup %s is not a cgroup2 subtree with cpu, memory and pids enabled", c->cgroup);
        return -1;
    }
    snprintf(g->name, sizeof g->name, "box-%d", box);
    if (remove_stale(g->jobs, g->name) != 0) {
        set_error(err, errlen, "stale execution group %s could not be reclaimed: %s", g->name, strerror(errno));
        return -2;
    }
    if (mkdirat(g->jobs, g->name, 0755) != 0) {
        g->name[0] = 0;
        set_error(err, errlen, "create execution group: %s", strerror(errno));
        return -1;
    }
    g->dir = openat(g->jobs, g->name, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    struct usage u;
    if (g->dir < 0 || configure(g->dir, l) != 0) {
        set_error(err, errlen, "configure execution group: %s", strerror(errno));
        return -1;
    }
    // 新组在启动任何进程前必须是干净的：没有进程，也没有计量历史。
    if (group_usage(g, &u) != 0 || u.populated || u.cpu_ns || u.oom || u.oom_kill || u.memory_max_events ||
        u.pids_max_events) {
        set_error(err, errlen, "new execution group has processes or accounting history");
        return -1;
    }
    return 0;
}
