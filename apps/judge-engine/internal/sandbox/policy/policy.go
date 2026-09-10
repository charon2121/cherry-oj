// Package policy 构造固定版本的 seccomp BPF。调用者不能通过请求扩展规则。
package policy

import "fmt"

// Instruction 的布局对应 Linux sock_filter；使用纯数据类型使策略可在非 Linux 上测试。
type Instruction struct {
	Code   uint16
	JT, JF uint8
	K      uint32
}

const (
	loadWord    = 0x20
	jumpEqual   = 0x15
	jumpSet     = 0x45
	ret         = 0x06
	killProcess = 0x80000000
	allow       = 0x7fff0000
	errno       = 0x00050000
	auditAMD64  = 0xc000003e
)

type Profile string

const (
	Command    Profile = "command-v1"
	Toolchain  Profile = "toolchain-v1"
	Supervisor Profile = "supervisor-v1"
)

// AMD64 仅生成 x86_64 原生 ABI 的策略；未知架构不会误用此系统调用表。
// 两种用户策略目前具有相同的宿主安全边界，后续收紧须变更版本和环境身份。
func AMD64(profile Profile) ([]Instruction, error) {
	if profile != Command && profile != Toolchain && profile != Supervisor {
		return nil, fmt.Errorf("未知 seccomp 策略 %q", profile)
	}
	p := []Instruction{
		{Code: loadWord, K: 4}, // seccomp_data.arch
		{Code: jumpEqual, JT: 1, K: auditAMD64},
		{Code: ret, K: killProcess},
		{Code: loadWord, K: 0},                // seccomp_data.nr
		{Code: jumpSet, JF: 1, K: 0x40000000}, // x32 ABI
		{Code: ret, K: killProcess},
	}
	// clone3 的指针结构不能用 classic BPF 安全解引用；拒绝并允许已验证 libc 回退 clone。
	p = append(p, Instruction{Code: jumpEqual, JF: 1, K: 435}, Instruction{Code: ret, K: errno | 38}) // ENOSYS
	// pidfd_open 是 Go os/exec 的可选能力探测；返回 ENOSYS 令其使用既有 wait/kill 路径。
	// 不开放 pidfd 访问，也不允许把宿主 FD 带进 payload。
	p = append(p, Instruction{Code: jumpEqual, JF: 1, K: 434}, Instruction{Code: ret, K: errno | 38})
	// isatty 不应导致程序被杀；所有 ioctl 都返回 ENOTTY，不向设备透传。
	p = append(p, Instruction{Code: jumpEqual, JF: 1, K: 16}, Instruction{Code: ret, K: errno | 25})
	// 仅允许进程名称、匿名 VMA 名称和权限状态查询；不允许修改安全状态。
	prctl := []Instruction{{Code: loadWord, K: 16}}
	for _, option := range []uint32{3, 15, 16, 21, 23, 39, 0x53564d41} {
		prctl = append(prctl, Instruction{Code: jumpEqual, JF: 1, K: option}, Instruction{Code: ret, K: allow})
	}
	prctl = append(prctl, Instruction{Code: ret, K: killProcess})
	p = append(p, Instruction{Code: jumpEqual, JF: uint8(len(prctl)), K: 157})
	p = append(p, prctl...)
	// clone 只允许普通 fork/线程所需 flags；排除 namespace、ptrace、CLONE_PARENT 等。
	// args[0] 是 64 位，先检查高半部，不能只检查低 32 位。
	const allowedCloneFlags uint32 = 0x100 | 0x200 | 0x400 | 0x800 | 0x4000 | 0x10000 | 0x40000 | 0x80000 | 0x100000 | 0x200000 | 0x1000000 | 0xff
	clone := []Instruction{
		{Code: loadWord, K: 20},
		{Code: jumpEqual, JT: 1, K: 0},
		{Code: ret, K: killProcess},
		{Code: loadWord, K: 16},
		{Code: jumpSet, JF: 1, K: ^allowedCloneFlags},
		{Code: ret, K: killProcess},
		{Code: 0x54, K: 0xff}, // AND：exit signal 仅允许 0 或 SIGCHLD。
		{Code: jumpEqual, JT: 2, K: 0},
		{Code: jumpEqual, JT: 1, K: 17},
		{Code: ret, K: killProcess},
		{Code: ret, K: allow},
	}
	p = append(p, Instruction{Code: jumpEqual, JF: uint8(len(clone)), K: 56})
	p = append(p, clone...)
	// 数字来自 Linux x86_64 syscall ABI，不因构建机器的 GOARCH 改变。
	// 允许普通 I/O、动态链接、进程/线程、信号和时钟；网络、提权、挂载与内核管理不在表中。
	calls := []struct {
		number uint32
		name   string
	}{
		{0, "read"},
		{1, "write"},
		{2, "open"},
		{3, "close"},
		{4, "stat"},
		{5, "fstat"},
		{6, "lstat"},
		{7, "poll"},
		{8, "lseek"},
		{9, "mmap"},
		{10, "mprotect"},
		{11, "munmap"},
		{12, "brk"},
		{13, "rt_sigaction"},
		{14, "rt_sigprocmask"},
		{15, "rt_sigreturn"},
		{17, "pread64"},
		{18, "pwrite64"},
		{19, "readv"},
		{20, "writev"},
		{21, "access"},
		{22, "pipe"},
		{23, "select"},
		{24, "sched_yield"},
		{25, "mremap"},
		{26, "msync"},
		{27, "mincore"},
		{28, "madvise"},
		{32, "dup"},
		{33, "dup2"},
		{35, "nanosleep"},
		{36, "getitimer"},
		{37, "alarm"},
		{38, "setitimer"},
		{39, "getpid"},
		{57, "fork"},
		{58, "vfork"},
		{60, "exit"},
		{61, "wait4"},
		{62, "kill"},
		{63, "uname"},
		{72, "fcntl"},
		{73, "flock"},
		{74, "fsync"},
		{75, "fdatasync"},
		{76, "truncate"},
		{77, "ftruncate"},
		{78, "getdents"},
		{79, "getcwd"},
		{80, "chdir"},
		{81, "fchdir"},
		{82, "rename"},
		{83, "mkdir"},
		{84, "rmdir"},
		{85, "creat"},
		{87, "unlink"},
		{89, "readlink"},
		{90, "chmod"},
		{91, "fchmod"},
		{95, "umask"},
		{96, "gettimeofday"},
		{97, "getrlimit"},
		{98, "getrusage"},
		{99, "sysinfo"},
		{100, "times"},
		{102, "getuid"},
		{104, "getgid"},
		{107, "geteuid"},
		{108, "getegid"},
		{110, "getppid"},
		{111, "getpgrp"},
		{112, "setsid"},
		{115, "getgroups"},
		{118, "getresuid"},
		{120, "getresgid"},
		{121, "getpgid"},
		{124, "getsid"},
		{125, "capget"},
		{127, "rt_sigpending"},
		{128, "rt_sigtimedwait"},
		{129, "rt_sigqueueinfo"},
		{130, "rt_sigsuspend"},
		{131, "sigaltstack"},
		{158, "arch_prctl"},
		{160, "setrlimit"},
		{186, "gettid"},
		{200, "tkill"},
		{201, "time"},
		{202, "futex"},
		{204, "sched_getaffinity"},
		{217, "getdents64"},
		{218, "set_tid_address"},
		{219, "restart_syscall"},
		{221, "fadvise64"},
		{228, "clock_gettime"},
		{229, "clock_getres"},
		{230, "clock_nanosleep"},
		{231, "exit_group"},
		{232, "epoll_wait"},
		{233, "epoll_ctl"},
		{234, "tgkill"},
		{235, "utimes"},
		{247, "waitid"},
		{257, "openat"},
		{258, "mkdirat"},
		{262, "newfstatat"},
		{263, "unlinkat"},
		{264, "renameat"},
		{267, "readlinkat"},
		{268, "fchmodat"},
		{269, "faccessat"},
		{270, "pselect6"},
		{271, "ppoll"},
		{273, "set_robust_list"},
		{274, "get_robust_list"},
		{280, "utimensat"},
		{281, "epoll_pwait"},
		{283, "timerfd_create"},
		{286, "timerfd_settime"},
		{287, "timerfd_gettime"},
		{289, "signalfd4"},
		{290, "eventfd2"},
		{291, "epoll_create1"},
		{292, "dup3"},
		{293, "pipe2"},
		{302, "prlimit64"},
		{309, "getcpu"},
		{318, "getrandom"},
		{324, "membarrier"},
		{332, "statx"},
		{334, "rseq"},
		{436, "close_range"},
		{439, "faccessat2"},
		{441, "epoll_pwait2"},
		{452, "fchmodat2"},
	}
	if profile != Supervisor {
		calls = append(calls, struct {
			number uint32
			name   string
		}{59, "execve"})
	} // execve：监督进程完成启动后不可再 exec。
	for _, nr := range calls {
		p = append(p, Instruction{Code: jumpEqual, JF: 1, K: nr.number}, Instruction{Code: ret, K: allow})
	}
	p = append(p, Instruction{Code: ret, K: killProcess})
	return p, nil
}
