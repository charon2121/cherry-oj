//go:build linux && amd64

// Package privilege 放弃进程的全部特权，P4 与 P5 都用它。
package privilege

import (
	"runtime"
	"syscall"
	"unsafe"

	"golang.org/x/sys/unix"
)

func allThreads(trap, a1, a2, a3 uintptr) syscall.Errno {
	_, _, err := syscall.AllThreadsSyscall(trap, a1, a2, a3)
	return err
}

const capabilityABIBits = 64 // LINUX_CAPABILITY_VERSION_3：两个 32 位 capability 字段。

// Drop 在所有 OS 线程上切换到 uid/gid，清空 capability 并设置 no_new_privs；不可逆。
// P4 放行 payload 前给自己降权，P5 在 execve 前给用户程序降权，两处共用同一套步骤。
func Drop(uid, gid int) syscall.Errno {
	// 当前 capability ABI 仅能表达 64 位；未来内核超出时必须拒绝，不能漏掉高位权限。
	_, _, boundErr := unix.RawSyscall6(unix.SYS_PRCTL, unix.PR_CAPBSET_READ, capabilityABIBits, 0, 0, 0, 0)
	if boundErr != unix.EINVAL {
		return unix.ENOTSUP
	}
	// 纯 Go 的 AllThreadsSyscall 在所有现存和未来线程上保持一致状态；cgo 构建会失败关闭。
	for capability := 0; capability < capabilityABIBits; capability++ {
		_, _, err := unix.RawSyscall6(unix.SYS_PRCTL, unix.PR_CAPBSET_READ, uintptr(capability), 0, 0, 0, 0)
		if err == unix.EINVAL {
			break
		}
		if err != 0 {
			return err
		}
		_, _, dropErr := syscall.AllThreadsSyscall6(unix.SYS_PRCTL, unix.PR_CAPBSET_DROP, uintptr(capability), 0, 0, 0, 0)
		if dropErr != 0 {
			return dropErr
		}
	}
	_, _, err := syscall.AllThreadsSyscall6(unix.SYS_PRCTL, unix.PR_CAP_AMBIENT, unix.PR_CAP_AMBIENT_CLEAR_ALL, 0, 0, 0, 0)
	if err != 0 {
		return err
	}
	if err = allThreads(unix.SYS_SETGROUPS, 0, 0, 0); err != 0 {
		return err
	}
	if err = allThreads(unix.SYS_SETRESGID, uintptr(gid), uintptr(gid), uintptr(gid)); err != 0 {
		return err
	}
	if err = allThreads(unix.SYS_SETRESUID, uintptr(uid), uintptr(uid), uintptr(uid)); err != 0 {
		return err
	}
	// bounding/ambient 清除不替代当前 capability 集合的清除；三者都不能留给 payload。
	header := unix.CapUserHeader{Version: unix.LINUX_CAPABILITY_VERSION_3}
	data := [2]unix.CapUserData{}
	// 指针转换直接位于 syscall 调用参数中，避免经普通 uintptr 包装函数丢失 GC/栈存活约束。
	_, _, err = syscall.AllThreadsSyscall(unix.SYS_CAPSET, uintptr(unsafe.Pointer(&header)), uintptr(unsafe.Pointer(&data[0])), 0)
	runtime.KeepAlive(&header)
	runtime.KeepAlive(&data)
	if err != 0 {
		return err
	}
	_, _, err = syscall.AllThreadsSyscall6(unix.SYS_PRCTL, unix.PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0, 0)
	return err
}
