//go:build linux && amd64

// Package execstage 是 P5：execve 用户程序之前，收紧自身的最后一段可信代码。
//
// Run 从 ExecConfigFD 读 ExecSpec，Exec 依次设置 rlimit、把 3 号以上 FD 标成 CLOEXEC、
// 在所有线程上降权、安装 seccomp，然后写 READY、等 GO，最后直接 execve。
// 任何一步失败都向 ExecErrorFD 写固定 8 字节记录（阶段 + errno）并 exit_group，不回到 Go 运行时。
// 装好 seccomp 之后只能用原始系统调用，不能再加入分配、日志、格式化之类的普通 Go 调用。
//
// 文件：exec（Run、Exec、失败记录）、spec（对 ExecSpec 的最后一道校验，不信任 P4）。
package execstage
