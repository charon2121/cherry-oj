// 请求格式：一串以 NUL 结尾的 `key=value` 记录。它由非 root 的调用方写入，这里逐项校验，
// 不补默认值——零预算的含义由上层决定，本程序不猜。
//
//   arg=<argv[i]>            1..256 条，argv[0] 必须是裸命令名
//   env=<KEY=VALUE>          0..128 条
//   input=<0|1>:<path>       0..128 条；1 表示可执行。内容在 box/in/<序号>
//   output=<path>            0..128 条；执行后要取回的文件
//   cpu_ns= clock_ns= memory_bytes= max_processes= stdout_max_bytes= stderr_max_bytes=  各恰好一条
//
// 不用 JSON：setuid-root 程序里少一个解析器，就少一块攻击面。
#define _GNU_SOURCE
#include <errno.h>
#include <stdlib.h>
#include <string.h>

#include "sandbox.h"

static bool name_char(char ch, const char *extra) {
    return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') ||
           (ch && strchr(extra, ch));
}

// 名称最多 128 个 ASCII 字节：首字符为字母数字，其余可含 extra 中的字符。
static bool valid_name(const char *s, size_t n, const char *extra) {
    if (n == 0 || n > 128 || !name_char(s[0], ""))
        return false;
    for (size_t i = 1; i < n; i++)
        if (!name_char(s[i], extra))
            return false;
    return true;
}

// 命令先在工作区、再在只读 rootfs 的固定目录解析，因此只接受裸名称。
bool valid_command(const char *p) { return valid_name(p, strlen(p), "_.+-"); }

// 工作区相对路径：最多 8 段，排除空段、点段和以点开头的内部文件。
bool valid_path(const char *p) {
    int segments = 0;
    for (;;) {
        const char *slash = strchr(p, '/');
        size_t n = slash ? (size_t)(slash - p) : strlen(p);
        if (++segments > MAX_PATH_SEGMENTS || !valid_name(p, n, "_.-"))
            return false;
        if (!slash)
            return true;
        p = slash + 1;
    }
}

static int parse_limit(const char *v, int64_t max, int64_t *out) {
    char *end;
    errno = 0;
    long long n = strtoll(v, &end, 10);
    if (errno || *v == 0 || *end || n < 0 || n > max)
        return -1;
    *out = n;
    return 0;
}

int spec_parse(char *data, size_t len, struct spec *s, char *err, size_t errlen) {
    memset(s, 0, sizeof *s);
    s->buffer = data;
    if (len == 0 || data[len - 1] != 0) {
        set_error(err, errlen, "spec must be NUL-terminated records");
        return -1;
    }
    struct {
        const char *key;
        int64_t *value;
        int64_t max;
        bool seen;
    } limits[] = {
        {"cpu_ns", &s->limits.cpu_ns, MAX_CPU_NS, false},
        {"clock_ns", &s->limits.clock_ns, MAX_CLOCK_NS, false},
        {"memory_bytes", &s->limits.memory_bytes, MAX_MEMORY_BYTES, false},
        {"max_processes", &s->limits.max_processes, MAX_PROCESSES, false},
        {"stdout_max_bytes", &s->limits.stdout_max_bytes, MAX_STREAM_BYTES, false},
        {"stderr_max_bytes", &s->limits.stderr_max_bytes, MAX_STREAM_BYTES, false},
    };
    size_t string_bytes = 0;
    for (char *rec = data, *next; rec < data + len; rec = next) {
        // 先算出下一条记录的位置：下面会把 '=' 改成 NUL，之后 strlen 只量得到键。
        next = rec + strlen(rec) + 1;
        char *eq = strchr(rec, '=');
        if (!eq) {
            set_error(err, errlen, "spec record without '='");
            return -1;
        }
        *eq = 0;
        const char *k = rec;
        char *v = eq + 1;
        if (!strcmp(k, "arg")) {
            if (s->nargs == MAX_ARGS)
                goto too_many;
            s->args[s->nargs++] = v;
            string_bytes += strlen(v) + 1;
        } else if (!strcmp(k, "env")) {
            char *e = strchr(v, '=');
            if (s->nenv == MAX_ENV)
                goto too_many;
            if (!e || e == v) {
                set_error(err, errlen, "malformed environment variable");
                return -1;
            }
            s->env[s->nenv++] = v;
            string_bytes += strlen(v) + 1;
        } else if (!strcmp(k, "input")) {
            if (s->ninputs == MAX_INPUTS)
                goto too_many;
            if ((v[0] != '0' && v[0] != '1') || v[1] != ':' || !valid_path(v + 2) ||
                strlen(v + 2) >= sizeof s->inputs[0].path) {
                set_error(err, errlen, "invalid input path");
                return -1;
            }
            for (int i = 0; i < s->ninputs; i++)
                if (!strcmp(s->inputs[i].path, v + 2)) {
                    set_error(err, errlen, "duplicate input path");
                    return -1;
                }
            struct input *in = &s->inputs[s->ninputs++];
            in->executable = v[0] == '1';
            strcpy(in->path, v + 2);
        } else if (!strcmp(k, "output")) {
            if (s->noutputs == MAX_OUTPUTS)
                goto too_many;
            if (!valid_path(v)) {
                set_error(err, errlen, "invalid artifact path");
                return -1;
            }
            for (int i = 0; i < s->noutputs; i++)
                if (!strcmp(s->outputs[i], v)) {
                    set_error(err, errlen, "duplicate artifact path");
                    return -1;
                }
            s->outputs[s->noutputs++] = v;
        } else {
            size_t i = 0;
            for (; i < sizeof limits / sizeof limits[0]; i++)
                if (!strcmp(k, limits[i].key))
                    break;
            if (i == sizeof limits / sizeof limits[0] || limits[i].seen ||
                parse_limit(v, limits[i].max, limits[i].value) != 0) {
                set_error(err, errlen, "invalid, repeated or unknown spec key %s", k);
                return -1;
            }
            limits[i].seen = true;
        }
    }
    for (size_t i = 0; i < sizeof limits / sizeof limits[0]; i++)
        if (!limits[i].seen) {
            set_error(err, errlen, "missing limit %s", limits[i].key);
            return -1;
        }
    // 零 CPU、墙钟、内存或进程数由上层直接给出资源结论，不应走到执行器。
    const struct limits *l = &s->limits;
    if (l->cpu_ns <= 0 || l->clock_ns <= 0 || l->memory_bytes <= 0 || l->max_processes <= 0) {
        set_error(err, errlen, "execution limits are not normalized");
        return -1;
    }
    if (s->nargs == 0 || !valid_command(s->args[0])) {
        set_error(err, errlen, "command must be a bare name");
        return -1;
    }
    if (string_bytes > MAX_STRING_BYTES) {
        set_error(err, errlen, "arguments/environment are too large");
        return -1;
    }
    return 0;
too_many:
    set_error(err, errlen, "too many arg, env, input or output records");
    return -1;
}
