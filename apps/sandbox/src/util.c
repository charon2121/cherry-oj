#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include "sandbox.h"

int64_t monotonic_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000 + ts.tv_nsec;
}

// 短写也要写完；否则后续字节会错位。
int write_all(int fd, const void *data, size_t len) {
    const char *p = data;
    while (len > 0) {
        ssize_t n = write(fd, p, len);
        if (n < 0 && errno == EINTR)
            continue;
        if (n <= 0)
            return -1;
        p += n;
        len -= (size_t)n;
    }
    return 0;
}

// 读取 dir 下的小文件（cgroupfs 等受信位置），超过容量视为错误，保证以 NUL 结尾。
int read_file_at(int dir, const char *name, char *buf, size_t cap, size_t *len) {
    int fd = openat(dir, name, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0)
        return -1;
    size_t total = 0;
    for (;;) {
        if (total + 1 >= cap) {
            close(fd);
            errno = EFBIG;
            return -1;
        }
        ssize_t n = read(fd, buf + total, cap - 1 - total);
        if (n < 0 && errno == EINTR)
            continue;
        if (n < 0) {
            int e = errno;
            close(fd);
            errno = e;
            return -1;
        }
        if (n == 0)
            break;
        total += (size_t)n;
    }
    close(fd);
    buf[total] = 0;
    if (len)
        *len = total;
    return 0;
}

int write_file_at(int dir, const char *name, const char *value) {
    int fd = openat(dir, name, O_WRONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0)
        return -1;
    int rc = write_all(fd, value, strlen(value));
    int e = errno;
    if (close(fd) != 0 && rc == 0)
        return -1;
    errno = e;
    return rc;
}

void set_error(char *err, size_t errlen, const char *fmt, ...) {
    if (!err || errlen == 0)
        return;
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(err, errlen, fmt, ap);
    va_end(ap);
}
