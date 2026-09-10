package policy

import (
	"fmt"
	"runtime"
	"unsafe"

	"golang.org/x/sys/unix"
)

// Install 在调用者已设置 no_new_privs、收敛全部线程权限后安装固定策略。
// TSYNC 失败可能返回线程 ID 而非 errno，二者都必须拒绝。成功后过滤不可撤回。
// 调用者须固定最终执行线程，并为安装后的执行/错误路径设计兼容的系统调用集合。
func Install(profile Profile) error {
	instructions, err := AMD64(profile)
	if err != nil {
		return err
	}
	filters := make([]unix.SockFilter, len(instructions))
	for i, in := range instructions {
		filters[i] = unix.SockFilter{Code: in.Code, Jt: in.JT, Jf: in.JF, K: in.K}
	}
	program := unix.SockFprog{Len: uint16(len(filters)), Filter: &filters[0]}
	result, _, errno := unix.RawSyscall(unix.SYS_SECCOMP, unix.SECCOMP_SET_MODE_FILTER, unix.SECCOMP_FILTER_FLAG_TSYNC, uintptr(unsafe.Pointer(&program)))
	runtime.KeepAlive(filters)
	if errno != 0 {
		return fmt.Errorf("加载 seccomp: %w", errno)
	}
	if result != 0 {
		return fmt.Errorf("seccomp TSYNC 未覆盖线程 %d", result)
	}
	return nil
}
