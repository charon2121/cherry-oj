// sandbox：一次性、setuid-root 的隔离执行器。每次调用执行一条命令，输出一行 JSON 事实后退出。
//
// 调用方（sandbox HTTP 服务，非 root）把请求写进 box 目录，再执行 `sandbox --box N`。
// 本程序不理解判题：编译、比对与 verdict 都在 judge。进程结构见 main.c 顶部。
#ifndef CHERRY_SANDBOX_H
#define CHERRY_SANDBOX_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>

#if !defined(__x86_64__)
#error "sandbox 只支持 x86_64：seccomp 策略按 x86_64 系统调用表编写"
#endif

#ifndef SANDBOX_CONFIG
#define SANDBOX_CONFIG "/etc/cherry-sandbox/executor.conf"
#endif

// 退出码：0 表示事实已输出且执行组已回收（其中可能带平台错误）；
// 1 表示拒绝执行，什么都没有启动；2 表示回收未确认，调用方必须停止接单。
enum { EXIT_FACTS = 0, EXIT_REFUSED = 1, EXIT_UNRECLAIMED = 2 };

// 请求硬边界，与原 hostexec 协议一致。调用方必须先填好默认值，这里只校验不补默认。
#define MAX_ARGS 256
#define MAX_ENV 128
#define MAX_INPUTS 128
#define MAX_OUTPUTS 128
#define MAX_STRING_BYTES (32 << 10) // argv 与 env 合计，含结尾 NUL
#define MAX_INPUT_BYTES (64LL << 20) // 输入文件与 stdin 合计
#define MAX_ARTIFACT_BYTES (64LL << 20)
#define MAX_PATH_SEGMENTS 8
#define MAX_SPEC_BYTES (256 << 10)
#define MAX_CPU_NS (60LL * 1000000000)
#define MAX_CLOCK_NS (120LL * 1000000000)
#define MAX_MEMORY_BYTES (1LL << 30)
#define MAX_PROCESSES 256
#define MAX_STREAM_BYTES (1 << 20)

// 工作区容量不等于进程内存限额；两者分别约束。
#define WORKSPACE_BYTES (128LL << 20)
#define WORKSPACE_INODES 4096
// 单进程 rlimit，不替代 cgroup 的整组计量。
#define PAYLOAD_NOFILE 256
#define PAYLOAD_FSIZE (64LL << 20)

#define CPU_SAMPLE_NS (5LL * 1000000)
#define STARTUP_TIMEOUT_NS (3LL * 1000000000)
#define RECLAIM_TIMEOUT_NS (10LL * 1000000000)

// ---- 受信配置：root 管理的本机文件，调用方不能选择路径或身份 ----
struct config {
    char rootfs[256];
    char boxes[256];
    char cgroup[256];
    uid_t service_uid;
    gid_t service_gid;
    uid_t payload_uid; // box N 使用 payload_uid + N
    gid_t payload_gid;
    uid_t init_uid;
    gid_t init_gid;
    int box_count;
};
int config_load(const char *path, struct config *c, char *err, size_t errlen);

// ---- 请求 ----
struct input {
    char path[1024];
    bool executable;
};
struct limits {
    int64_t cpu_ns, clock_ns, memory_bytes, max_processes, stdout_max_bytes, stderr_max_bytes;
};
struct spec {
    char *args[MAX_ARGS + 1];
    int nargs;
    char *env[MAX_ENV + 1];
    int nenv;
    struct input inputs[MAX_INPUTS];
    int ninputs;
    char *outputs[MAX_OUTPUTS];
    int noutputs;
    struct limits limits;
    char *buffer; // 所有字符串都指向它
};
int spec_parse(char *data, size_t len, struct spec *s, char *err, size_t errlen);
bool valid_path(const char *p);
bool valid_command(const char *p);

// ---- box：调用方的工作目录，内容一律视为不可信 ----
struct box {
    int index;
    int dir;    // boxes/<N>，持有 flock 独占锁
    int in;     // boxes/<N>/in：输入文件按序号命名 0、1、2……
    int out;    // boxes/<N>/out：本程序写回 stdout、stderr 与 artifact-<序号>
    int stdin_file;
};
int box_open(const struct config *c, int index, struct box *b, char *err, size_t errlen);
int box_read_spec(const struct box *b, const struct config *c, char **data, size_t *len, char *err, size_t errlen);
int box_open_input(const struct box *b, const struct config *c, int index, int64_t *size);
int box_write_file(const struct box *b, const struct config *c, const char *name, const void *data, size_t len);
int box_copy_artifact(const struct box *b, const struct config *c, const char *name, int src, int64_t size);

// ---- 执行组 ----
struct usage {
    int64_t cpu_ns, memory_bytes;
    uint64_t oom, oom_kill, memory_max_events, pids_max_events;
    bool populated;
};
struct jobgroup {
    int jobs; // 委派的 jobs 子树
    int dir;  // jobs/box-<N>
    char name[32];
};
int group_create(const struct config *c, int box, const struct limits *l, struct jobgroup *g, char *err, size_t errlen);
int group_usage(const struct jobgroup *g, struct usage *u);
int group_stop(const struct jobgroup *g, struct usage *final); // 整组 kill 并等待清空
int group_remove(struct jobgroup *g);

// ---- namespace 内：init 与用户程序 ----
struct child_plan {
    const struct config *config;
    const struct spec *spec;
    const struct box *box;
    int box_index;
    int workspace; // fsmount 得到的独立 tmpfs
    int stdout_w, stderr_w;
    int exec_error_w; // CLOEXEC：execve 成功即关闭
    int report_w;     // init 向父进程报告用户程序退出或自身失败
    int report_r;     // 父进程的读端；init 必须关掉它，才能从写端看出父进程是否还在
};
_Noreturn void init_main(const struct child_plan *p);

// init 通过 report 管道发出的固定 12 字节记录。
enum { REPORT_EXIT = 1, REPORT_INIT_FAILED = 2 };
struct report {
    uint32_t kind;
    int32_t a, b; // EXIT：退出码、信号；INIT_FAILED：阶段、errno
};
// 用户程序 execve 前失败时写入的固定 8 字节记录。
struct exec_failure {
    uint32_t stage;
    int32_t err;
};
#define FAILURE_EXIT_CODE 125

// ---- seccomp ----
enum filter_profile { FILTER_PAYLOAD, FILTER_SUPERVISOR };
int filter_install(enum filter_profile profile);

// ---- 降权 ----
int drop_privileges(uid_t uid, gid_t gid);

// ---- 事实与结论 ----
enum reason { REASON_NONE, REASON_CPU, REASON_WALL, REASON_OUTPUT, REASON_CANCELLED, REASON_PLATFORM };
struct supervision {
    enum reason reason;
    char failure[256]; // 非空即平台故障
    bool exited;
    int exit_code, signal;
    bool report_lost; // init 没能报告退出事实，可能由本任务 OOM 造成
    bool started;
};
struct facts {
    struct supervision supervision;
    bool cancelled;
    bool output_exceeded;
    struct usage usage;
    bool usage_valid;
    int64_t cpu_ns, clock_ns;
    int64_t cpu_budget;
};
struct conclusion {
    enum reason reason;
    int signal;
    char failure[512];
};
void conclude(const struct facts *f, struct conclusion *c);
const char *reason_name(enum reason r);

// ---- 监督 ----
struct run {
    struct facts facts;
    struct conclusion conclusion;
    char *stdout_data, *stderr_data;
    size_t stdout_len, stderr_len;
    int workspace; // 已停止的工作区，产物从这里取
    bool reclaimed;
};
int execute(const struct config *c, const struct spec *s, const struct box *b, struct jobgroup *g, struct run *r);

// ---- 小工具 ----
int64_t monotonic_ns(void);
int write_all(int fd, const void *data, size_t len);
int read_file_at(int dir, const char *name, char *buf, size_t cap, size_t *len);
int write_file_at(int dir, const char *name, const char *value);
void set_error(char *err, size_t errlen, const char *fmt, ...) __attribute__((format(printf, 3, 4)));

#endif
