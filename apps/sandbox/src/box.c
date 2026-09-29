// box 是调用方（非 root 的 sandbox HTTP 服务）准备的工作目录：
//
//   <boxes>/<N>/spec       请求
//   <boxes>/<N>/stdin      标准输入
//   <boxes>/<N>/in/<序号>   输入文件，序号对应 spec 中 input 的顺序
//   <boxes>/<N>/out/       必须为空；本程序写回 stdout、stderr 与 artifact-<序号>
//
// 这里以 root 身份操作一个由非 root 控制的目录，所以：路径中不跟随任何符号链接；打开的每个目录
// 与文件都必须属于服务身份；读取只接受单链接的普通文件；写回只用 O_EXCL 新建并把所有者交还服务。
// 这样 root 读到的只是服务本来就能读的文件，写出的也只是服务自己的文件。
#define _GNU_SOURCE
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/openat2.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#include "sandbox.h"

#define RESOLVE_STRICT (RESOLVE_BENEATH | RESOLVE_NO_SYMLINKS | RESOLVE_NO_MAGICLINKS | RESOLVE_NO_XDEV)

static int openat2_strict(int dir, const char *path, int flags, mode_t mode, uint64_t resolve) {
    struct open_how how = {.flags = (uint64_t)flags | O_CLOEXEC, .mode = mode, .resolve = resolve};
    return (int)syscall(SYS_openat2, dir, path, &how, sizeof how);
}

static bool owned_by_service(int fd, const struct config *c, mode_t type) {
    struct stat st;
    return fstat(fd, &st) == 0 && (st.st_mode & S_IFMT) == type && st.st_uid == c->service_uid &&
           (st.st_mode & 022) == 0;
}

static bool directory_empty(int dir) {
    int fd = openat(dir, ".", O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    DIR *d = fd < 0 ? NULL : fdopendir(fd);
    if (!d) {
        if (fd >= 0)
            close(fd);
        return false;
    }
    bool empty = true;
    for (struct dirent *e; (e = readdir(d));)
        if (strcmp(e->d_name, ".") && strcmp(e->d_name, ".."))
            empty = false;
    closedir(d);
    return empty;
}

int box_open(const struct config *c, int index, struct box *b, char *err, size_t errlen) {
    memset(b, 0, sizeof *b);
    b->index = index;
    b->dir = b->in = b->out = b->stdin_file = -1;
    // boxes 本身属于服务身份；路径解析拒绝符号链接，打开后再核对所有者。
    int root = openat2_strict(AT_FDCWD, c->boxes, O_RDONLY | O_DIRECTORY, 0, RESOLVE_NO_SYMLINKS | RESOLVE_NO_MAGICLINKS);
    if (root < 0 || !owned_by_service(root, c, S_IFDIR)) {
        if (root >= 0)
            close(root);
        set_error(err, errlen, "boxes directory missing or not owned by the service");
        return -1;
    }
    char name[16];
    snprintf(name, sizeof name, "%d", index);
    b->dir = openat2_strict(root, name, O_RDONLY | O_DIRECTORY, 0, RESOLVE_STRICT);
    close(root);
    if (b->dir < 0 || !owned_by_service(b->dir, c, S_IFDIR)) {
        set_error(err, errlen, "box %d missing or not owned by the service", index);
        return -1;
    }
    // 同一 box 同时只允许一次执行：box 决定身份与执行组名，并发复用会互相干扰。
    if (flock(b->dir, LOCK_EX | LOCK_NB) != 0) {
        set_error(err, errlen, "box %d is busy", index);
        return -1;
    }
    b->in = openat2_strict(b->dir, "in", O_RDONLY | O_DIRECTORY, 0, RESOLVE_STRICT);
    b->out = openat2_strict(b->dir, "out", O_RDONLY | O_DIRECTORY, 0, RESOLVE_STRICT);
    if (b->in < 0 || b->out < 0 || !owned_by_service(b->in, c, S_IFDIR) || !owned_by_service(b->out, c, S_IFDIR)) {
        set_error(err, errlen, "box %d in/out directories are missing or not owned by the service", index);
        return -1;
    }
    if (!directory_empty(b->out)) {
        set_error(err, errlen, "box %d out directory is not empty", index);
        return -1;
    }
    int64_t size;
    b->stdin_file = -1;
    int fd = openat2_strict(b->dir, "stdin", O_RDONLY | O_NOFOLLOW | O_NONBLOCK, 0, RESOLVE_STRICT);
    struct stat st;
    if (fd < 0 || !owned_by_service(fd, c, S_IFREG) || fstat(fd, &st) != 0 || st.st_nlink != 1 ||
        (size = st.st_size) > MAX_INPUT_BYTES) {
        if (fd >= 0)
            close(fd);
        set_error(err, errlen, "box %d stdin missing or not a bounded regular file", index);
        return -1;
    }
    b->stdin_file = fd;
    return 0;
}

// 读取一份单链接、属于服务的普通文件；O_NONBLOCK 防止 FIFO 在检查前就把 root 阻塞在 open。
static int open_regular(int dir, const struct config *c, const char *name, int64_t *size) {
    int fd = openat2_strict(dir, name, O_RDONLY | O_NOFOLLOW | O_NONBLOCK, 0, RESOLVE_STRICT);
    struct stat st;
    if (fd < 0)
        return -1;
    if (!owned_by_service(fd, c, S_IFREG) || fstat(fd, &st) != 0 || st.st_nlink != 1) {
        close(fd);
        errno = EPERM;
        return -1;
    }
    *size = st.st_size;
    return fd;
}

int box_read_spec(const struct box *b, const struct config *c, char **data, size_t *len, char *err, size_t errlen) {
    int64_t size;
    int fd = open_regular(b->dir, c, "spec", &size);
    if (fd < 0 || size <= 0 || size > MAX_SPEC_BYTES) {
        if (fd >= 0)
            close(fd);
        set_error(err, errlen, "box spec missing or not a bounded regular file");
        return -1;
    }
    char *buf = malloc((size_t)size);
    size_t got = 0;
    while (buf && got < (size_t)size) {
        ssize_t n = read(fd, buf + got, (size_t)size - got);
        if (n < 0 && errno == EINTR)
            continue;
        if (n <= 0)
            break;
        got += (size_t)n;
    }
    close(fd);
    if (!buf || got != (size_t)size) {
        free(buf);
        set_error(err, errlen, "box spec changed while reading");
        return -1;
    }
    *data = buf;
    *len = got;
    return 0;
}

int box_open_input(const struct box *b, const struct config *c, int index, int64_t *size) {
    char name[16];
    snprintf(name, sizeof name, "%d", index);
    return open_regular(b->in, c, name, size);
}

static int create_output(const struct box *b, const struct config *c, const char *name) {
    int fd = openat2_strict(b->out, name, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, 0600, RESOLVE_STRICT);
    if (fd < 0)
        return -1;
    if (fchown(fd, c->service_uid, c->service_gid) != 0) {
        close(fd);
        return -1;
    }
    return fd;
}

int box_write_file(const struct box *b, const struct config *c, const char *name, const void *data, size_t len) {
    int fd = create_output(b, c, name);
    if (fd < 0)
        return -1;
    int rc = write_all(fd, data, len);
    return close(fd) != 0 ? -1 : rc;
}

// 复制恰好 size 字节；源文件在复制期间变长或变短都视为失败，不能交付被截断的产物。
int box_copy_artifact(const struct box *b, const struct config *c, const char *name, int src, int64_t size) {
    int fd = create_output(b, c, name);
    if (fd < 0)
        return -1;
    char buf[64 << 10];
    int64_t left = size;
    int rc = 0;
    while (left > 0 && rc == 0) {
        ssize_t n = read(src, buf, left < (int64_t)sizeof buf ? (size_t)left : sizeof buf);
        if (n < 0 && errno == EINTR)
            continue;
        if (n <= 0)
            rc = -1;
        else {
            rc = write_all(fd, buf, (size_t)n);
            left -= n;
        }
    }
    if (rc == 0 && read(src, buf, 1) != 0)
        rc = -1;
    return close(fd) != 0 ? -1 : rc;
}
