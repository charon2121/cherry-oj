// 受信配置。它决定 rootfs、box 目录、cgroup 子树和各 box 的身份，所以只接受 root 管理的文件：
// 文件与每一级祖先都必须属于 root、不能被组或其他人写入，路径中不能有符号链接。
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "sandbox.h"

static bool root_protected(int fd) {
    struct stat st;
    return fstat(fd, &st) == 0 && st.st_uid == 0 && (st.st_mode & 022) == 0;
}

// 从根目录逐级打开，每一级都核对所有权与权限，最后一级按普通文件打开。
static int open_protected(const char *path) {
    if (path[0] != '/')
        return -1;
    char copy[PATH_MAX];
    if (strlen(path) >= sizeof copy)
        return -1;
    strcpy(copy, path);
    int dir = open("/", O_PATH | O_DIRECTORY | O_CLOEXEC);
    if (dir < 0 || !root_protected(dir))
        goto fail;
    char *save = NULL;
    char *part = strtok_r(copy + 1, "/", &save);
    while (part) {
        char *next = strtok_r(NULL, "/", &save);
        if (strcmp(part, ".") == 0 || strcmp(part, "..") == 0)
            goto fail;
        int flags = next ? O_PATH | O_DIRECTORY : O_RDONLY | O_NONBLOCK;
        int fd = openat(dir, part, flags | O_NOFOLLOW | O_CLOEXEC);
        if (fd < 0 || !root_protected(fd)) {
            if (fd >= 0)
                close(fd);
            goto fail;
        }
        close(dir);
        dir = fd;
        part = next;
    }
    return dir;
fail:
    if (dir >= 0)
        close(dir);
    return -1;
}

static int parse_id(const char *v, long long max, long long *out) {
    char *end;
    errno = 0;
    long long n = strtoll(v, &end, 10);
    if (errno || *v == 0 || *end || n <= 0 || n > max)
        return -1;
    *out = n;
    return 0;
}

static int set_path(char *dst, size_t cap, const char *v) {
    if (v[0] != '/' || strlen(v) >= cap || strstr(v, "/../") || strstr(v, "/./") || strstr(v, "//"))
        return -1;
    strcpy(dst, v);
    return 0;
}

// 每个 box 有自己的一对身份：payload 与 init 各占一段 n 个连续编号，两段不重叠；
// 服务身份只有一个编号，不能落在任一段内。
static bool overlaps(long long a, long long b, int n) { return a < b + n && b < a + n; }
static bool inside(long long x, long long base, int n) { return x >= base && x < base + n; }

static int validate(const struct config *c, char *err, size_t errlen) {
    if (!c->rootfs[0] || !c->boxes[0] || !c->cgroup[0]) {
        set_error(err, errlen, "config: rootfs, boxes and cgroup are required");
        return -1;
    }
    if (c->box_count < 1 || c->box_count > 4) {
        set_error(err, errlen, "config: box_count must be 1 to 4");
        return -1;
    }
    int n = c->box_count;
    long long ids[] = {c->payload_uid, c->init_uid};
    long long gids[] = {c->payload_gid, c->init_gid};
    if (!c->service_uid || !c->service_gid || !ids[0] || !ids[1] || !gids[0] || !gids[1] ||
        overlaps(ids[0], ids[1], n) || overlaps(gids[0], gids[1], n) || inside(c->service_uid, ids[0], n) ||
        inside(c->service_uid, ids[1], n) || inside(c->service_gid, gids[0], n) ||
        inside(c->service_gid, gids[1], n)) {
        set_error(err, errlen, "config: service, init and payload identities must be separate and non-root");
        return -1;
    }
    return 0;
}

int config_load(const char *path, struct config *c, char *err, size_t errlen) {
    memset(c, 0, sizeof *c);
    int fd = open_protected(path);
    if (fd < 0) {
        set_error(err, errlen, "config %s is missing or not protected by root", path);
        return -1;
    }
    struct stat st;
    char buf[16 << 10];
    size_t len = 0;
    if (fstat(fd, &st) != 0 || !S_ISREG(st.st_mode) || st.st_size >= (off_t)sizeof buf) {
        close(fd);
        set_error(err, errlen, "config is not a bounded regular file");
        return -1;
    }
    while (len < sizeof buf - 1) {
        ssize_t r = read(fd, buf + len, sizeof buf - 1 - len);
        if (r < 0 && errno == EINTR)
            continue;
        if (r <= 0)
            break;
        len += (size_t)r;
    }
    close(fd);
    buf[len] = 0;
    char *save = NULL;
    for (char *line = strtok_r(buf, "\n", &save); line; line = strtok_r(NULL, "\n", &save)) {
        if (line[0] == '#' || line[0] == 0)
            continue;
        char *eq = strchr(line, '=');
        if (!eq) {
            set_error(err, errlen, "config: malformed line");
            return -1;
        }
        *eq = 0;
        const char *k = line, *v = eq + 1;
        long long n = 0;
        int rc;
        if (!strcmp(k, "rootfs"))
            rc = set_path(c->rootfs, sizeof c->rootfs, v);
        else if (!strcmp(k, "boxes"))
            rc = set_path(c->boxes, sizeof c->boxes, v);
        else if (!strcmp(k, "cgroup"))
            rc = set_path(c->cgroup, sizeof c->cgroup, v);
        else if (!strcmp(k, "box_count"))
            rc = parse_id(v, 4, &n), c->box_count = (int)n;
        else if (!strcmp(k, "service_uid"))
            rc = parse_id(v, 0xfffffff0, &n), c->service_uid = (uid_t)n;
        else if (!strcmp(k, "service_gid"))
            rc = parse_id(v, 0xfffffff0, &n), c->service_gid = (gid_t)n;
        else if (!strcmp(k, "payload_uid"))
            rc = parse_id(v, 0xfffffff0, &n), c->payload_uid = (uid_t)n;
        else if (!strcmp(k, "payload_gid"))
            rc = parse_id(v, 0xfffffff0, &n), c->payload_gid = (gid_t)n;
        else if (!strcmp(k, "init_uid"))
            rc = parse_id(v, 0xfffffff0, &n), c->init_uid = (uid_t)n;
        else if (!strcmp(k, "init_gid"))
            rc = parse_id(v, 0xfffffff0, &n), c->init_gid = (gid_t)n;
        else
            rc = -1;
        if (rc != 0) {
            set_error(err, errlen, "config: invalid or unknown key %s", k);
            return -1;
        }
    }
    return validate(c, err, errlen);
}
