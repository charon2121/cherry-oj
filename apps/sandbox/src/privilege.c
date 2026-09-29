// 不可逆地放弃特权：清空 capability 边界集与 ambient 集，切换到 uid/gid，
// 清空当前 capability，设置 no_new_privs。本程序是单线程的，调用一次即覆盖整个进程。
#define _GNU_SOURCE
#include <errno.h>
#include <grp.h>
#include <linux/capability.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

#include "sandbox.h"

int drop_privileges(uid_t uid, gid_t gid) {
    // capability ABI 只能表达 64 位；内核若超出这个范围必须拒绝，不能漏掉高位权限。
    if (prctl(PR_CAPBSET_READ, 64, 0, 0, 0) != -1 || errno != EINVAL)
        return -1;
    for (int cap = 0; cap < 64; cap++) {
        if (prctl(PR_CAPBSET_READ, cap, 0, 0, 0) < 0) {
            if (errno == EINVAL)
                break;
            return -1;
        }
        if (prctl(PR_CAPBSET_DROP, cap, 0, 0, 0) != 0)
            return -1;
    }
    if (prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_CLEAR_ALL, 0, 0, 0) != 0)
        return -1;
    if (setgroups(0, NULL) != 0 || setresgid(gid, gid, gid) != 0 || setresuid(uid, uid, uid) != 0)
        return -1;
    // 边界集与 ambient 集的清除不等于当前集合的清除；三者都不能留给后续代码。
    struct __user_cap_header_struct header = {.version = _LINUX_CAPABILITY_VERSION_3};
    struct __user_cap_data_struct data[2] = {{0}};
    if (syscall(SYS_capset, &header, data) != 0)
        return -1;
    return prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0);
}
