package execstage

import (
	"errors"
	"os"
	"runtime"
	"syscall"
	"unsafe"

	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/isolator/privilege"
	"cherry-oj/judge-engine/isolator/seccomp"
	"cherry-oj/judge-engine/isolator/startup"

	"golang.org/x/sys/unix"
)

// Run 是 P5 的入口：从 ExecConfigFD 读取 P4 写入的 ExecSpec 后交给 Exec，永不返回。
// main 必须在读取配置、启动服务或建立任何 goroutine 之前调用它。
func Run() {
	config := os.NewFile(startup.ExecConfigFD, "exec-config")
	var s startup.ExecSpec
	err := hostexec.ReadFrame(config, &s, hostexec.MaxFrameBytes)
	if errors.Join(err, config.Close()) != nil {
		os.Exit(startup.FailureExitCode)
	}
	Exec(s)
}

// Exec 只能由新建的专用 payload re-exec 进程调用，永不返回。
// 入组、namespace 和 rootfs 由上游先完成；本函数设置最终权限/过滤后参与 READY/GO 握手，
// 收到 GO 才 execve。不能在上游握手前运行任何用户代码。
// 出错即终止整个可信进程，不能回到普通 Go 服务循环继续接请求。
func Exec(s startup.ExecSpec) {
	runtime.LockOSThread()
	// 不 UnlockOSThread：最终执行线程的所有安全状态必须连续到 exec。
	if err := validate(s); err != nil {
		terminalFailure(s.ErrorFD, startup.ExecFailureConfig, unix.EINVAL)
	}
	// 所有字符串与 argv/envp 指针在最终策略安装前准备，exec 后由内核接管。
	path, err := unix.BytePtrFromString(s.Path)
	if err != nil {
		terminalFailure(s.ErrorFD, startup.ExecFailureConfig, unix.EINVAL)
	}
	args, err := syscall.SlicePtrFromStrings(s.Args)
	if err != nil {
		terminalFailure(s.ErrorFD, startup.ExecFailureConfig, unix.EINVAL)
	}
	env, err := syscall.SlicePtrFromStrings(s.Env)
	if err != nil {
		terminalFailure(s.ErrorFD, startup.ExecFailureConfig, unix.EINVAL)
	}
	for resource, limit := range map[int]uint64{syscall.RLIMIT_CORE: 0, syscall.RLIMIT_NOFILE: s.NoFile, syscall.RLIMIT_FSIZE: s.FileSizeBytes} {
		if err := syscall.Setrlimit(resource, &syscall.Rlimit{Cur: limit, Max: limit}); err != nil {
			terminalFailure(s.ErrorFD, startup.ExecFailureRlimit, failureErrno(err))
		}
	}
	// 标记所有非标准 FD 为 CLOEXEC，不提前关闭 Go runtime 正在使用的 FD。
	// close_range(CLOEXEC) 不允许不可信代码执行前的任何旧根、IPC、memfd 句柄跨 exec。
	if err := unix.CloseRange(startup.ExtraFilesBaseFD, ^uint(0), unix.CLOSE_RANGE_CLOEXEC); err != nil {
		terminalFailure(s.ErrorFD, startup.ExecFailureCloseOnExec, failureErrno(err))
	}
	if err := privilege.Drop(s.UID, s.GID); err != 0 {
		terminalFailure(s.ErrorFD, startup.ExecFailurePrivileges, err)
	}
	if err := seccomp.Install(s.Profile); err != nil {
		terminalFailure(s.ErrorFD, startup.ExecFailureSeccomp, failureErrno(err))
	}
	if s.ReadyFD != 0 {
		ready := [startup.HandshakeBytes]byte{startup.PayloadReady}
		n, _, e := unix.RawSyscall(unix.SYS_WRITE, uintptr(s.ReadyFD), uintptr(unsafe.Pointer(&ready[0])), startup.HandshakeBytes)
		if e != 0 || n != startup.HandshakeBytes {
			terminalFailure(s.ErrorFD, startup.ExecFailureHandshake, unix.EPIPE)
		}
		n, _, e = unix.RawSyscall(unix.SYS_READ, uintptr(s.ReadyFD), uintptr(unsafe.Pointer(&ready[0])), startup.HandshakeBytes)
		if e != 0 || n != startup.HandshakeBytes || ready[0] != startup.PayloadGo {
			terminalFailure(s.ErrorFD, startup.ExecFailureHandshake, unix.EPIPE)
		}
		unix.RawSyscall(unix.SYS_CLOSE, uintptr(s.ReadyFD), 0, 0)
	}
	// 使用受维护 syscall 包的原始 exec 系统调用，不引入 fork、vfork 汇编或 runtime 钩子。
	// 直接 exec 避免标准 Exec 包装器在最终过滤后再构造参数或恢复旧 NOFILE。
	_, _, execErr := unix.RawSyscall(unix.SYS_EXECVE, uintptr(unsafe.Pointer(path)), uintptr(unsafe.Pointer(&args[0])), uintptr(unsafe.Pointer(&env[0])))
	runtime.KeepAlive(path)
	runtime.KeepAlive(args)
	runtime.KeepAlive(env)
	terminalFailure(s.ErrorFD, startup.ExecFailureExecve, execErr)
}

// terminalFailure 可能在 seccomp 安装后运行，不能改用日志、格式化或普通退出清理。
// 固定记录只报告阶段和 errno；使用 exit_group 防止其他 Go 线程继续运行。
func terminalFailure(fd int, stage startup.ExecFailureStage, errno syscall.Errno) {
	// 直接拼装小端字节；过滤器安装后不引入编码器或新的普通 Go 调用。
	record := [startup.ExecFailureRecordBytes]byte{
		startup.ExecFailureStageOffset: byte(stage),
		startup.ExecFailureErrnoOffset: byte(errno), byte(errno >> 8), byte(errno >> 16), byte(errno >> 24),
	}
	if fd >= startup.ExtraFilesBaseFD {
		_, _, _ = unix.RawSyscall(unix.SYS_WRITE, uintptr(fd), uintptr(unsafe.Pointer(&record[0])), uintptr(len(record)))
	}
	for {
		_, _, _ = unix.RawSyscall(unix.SYS_EXIT_GROUP, startup.FailureExitCode, 0, 0)
	}
}
