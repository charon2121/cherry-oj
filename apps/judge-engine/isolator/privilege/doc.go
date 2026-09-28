//go:build linux && amd64

// Package privilege 不可逆地放弃进程的全部特权：清空 capability、切换 UID/GID、设置
// no_new_privs，并对所有 OS 线程生效。P4 放行前给自己降权，P5 在 execve 前给用户程序降权，
// 两处共用同一套步骤，所以单独成包，避免两个进程角色互相依赖。
package privilege
