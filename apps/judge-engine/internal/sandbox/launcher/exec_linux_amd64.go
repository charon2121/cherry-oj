package launcher

import (
	"runtime"
	"syscall"
	"unsafe"

	"cherry-oj/judge-engine/internal/sandbox/policy"
	"golang.org/x/sys/unix"
)

// RunExecStage 只能由新建的专用 payload re-exec 进程调用，永不返回。
// 入组、namespace、rootfs 和握手必须由上游启动器先完成；本函数只负责最终权限/过滤/exec。
// 出错即终止整个可信进程，不能回到普通 Go 服务循环继续接请求。
func RunExecStage(s ExecSpec) {
	runtime.LockOSThread()
	// 不 UnlockOSThread：最终执行线程的所有安全状态必须连续到 exec。
	if err := s.validate(); err != nil {
		terminalFailure(s.ErrorFD, 1, unix.EINVAL)
	}
	// 所有字符串与 argv/envp 指针在最终策略安装前准备，exec 后由内核接管。
	path, err := unix.BytePtrFromString(s.Path)
	if err != nil {
		terminalFailure(s.ErrorFD, 1, unix.EINVAL)
	}
	args, err := syscall.SlicePtrFromStrings(s.Args)
	if err != nil {
		terminalFailure(s.ErrorFD, 1, unix.EINVAL)
	}
	env, err := syscall.SlicePtrFromStrings(s.Env)
	if err != nil {
		terminalFailure(s.ErrorFD, 1, unix.EINVAL)
	}
	for resource, limit := range map[int]uint64{syscall.RLIMIT_CORE: 0, syscall.RLIMIT_NOFILE: s.NoFile, syscall.RLIMIT_FSIZE: s.FileSizeBytes} {
		if err := syscall.Setrlimit(resource, &syscall.Rlimit{Cur: limit, Max: limit}); err != nil {
			terminalFailure(s.ErrorFD, 2, failureErrno(err))
		}
	}
	// 标记所有非标准 FD 为 CLOEXEC，不提前关闭 Go runtime 正在使用的 FD。
	// close_range(CLOEXEC) 不允许不可信代码执行前的任何旧根、IPC、memfd 句柄跨 exec。
	if err := unix.CloseRange(3, ^uint(0), unix.CLOSE_RANGE_CLOEXEC); err != nil {
		terminalFailure(s.ErrorFD, 3, failureErrno(err))
	}
	if err := dropPrivileges(s.UID, s.GID); err != 0 {
		terminalFailure(s.ErrorFD, 4, err)
	}
	if err := policy.Install(s.Profile); err != nil {
		terminalFailure(s.ErrorFD, 5, failureErrno(err))
	}
	if s.ReadyFD != 0 {
		ready := [1]byte{'R'}
		n, _, e := unix.RawSyscall(unix.SYS_WRITE, uintptr(s.ReadyFD), uintptr(unsafe.Pointer(&ready[0])), 1)
		if e != 0 || n != 1 {
			terminalFailure(s.ErrorFD, 7, unix.EPIPE)
		}
		n, _, e = unix.RawSyscall(unix.SYS_READ, uintptr(s.ReadyFD), uintptr(unsafe.Pointer(&ready[0])), 1)
		if e != 0 || n != 1 || ready[0] != 'G' {
			terminalFailure(s.ErrorFD, 7, unix.EPIPE)
		}
		unix.RawSyscall(unix.SYS_CLOSE, uintptr(s.ReadyFD), 0, 0)
	}
	// 使用受维护 syscall 包的原始 exec 系统调用，不引入 fork、vfork 汇编或 runtime 钩子。
	// 直接 exec 避免标准 Exec 包装器在最终过滤后再构造参数或恢复旧 NOFILE。
	_, _, execErr := unix.RawSyscall(unix.SYS_EXECVE, uintptr(unsafe.Pointer(path)), uintptr(unsafe.Pointer(&args[0])), uintptr(unsafe.Pointer(&env[0])))
	runtime.KeepAlive(path)
	runtime.KeepAlive(args)
	runtime.KeepAlive(env)
	terminalFailure(s.ErrorFD, 6, execErr)
}

func allThreads(trap, a1, a2, a3 uintptr) syscall.Errno {
	_, _, err := syscall.AllThreadsSyscall(trap, a1, a2, a3)
	return err
}

func dropPrivileges(uid, gid int) syscall.Errno {
	// 当前 capability ABI 仅能表达 64 位；未来内核超出时必须拒绝，不能漏掉高位权限。
	_, _, boundErr := unix.RawSyscall6(unix.SYS_PRCTL, unix.PR_CAPBSET_READ, 64, 0, 0, 0, 0)
	if boundErr != unix.EINVAL {
		return unix.ENOTSUP
	}
	// 纯 Go 的 AllThreadsSyscall 在所有现存和未来线程上保持一致状态；cgo 构建会失败关闭。
	for capability := 0; capability < 64; capability++ {
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

// terminalFailure 的固定 8 字节只报告阶段和 errno，不包含用户代码、宿主路径或凭据。
func terminalFailure(fd int, stage byte, errno syscall.Errno) {
	record := [8]byte{stage, 0, 0, 0, byte(errno), byte(errno >> 8), byte(errno >> 16), byte(errno >> 24)}
	if fd >= 3 {
		_, _, _ = unix.RawSyscall(unix.SYS_WRITE, uintptr(fd), uintptr(unsafe.Pointer(&record[0])), uintptr(len(record)))
	}
	for {
		_, _, _ = unix.RawSyscall(unix.SYS_EXIT_GROUP, 125, 0, 0)
	}
}
