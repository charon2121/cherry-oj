//go:build linux && amd64

// Package startup 定义 P3、P4、P5 之间的启动协议：继承 FD 的编号、控制事件、两个配置帧
// （StageSpec、ExecSpec）和 READY/GO 握手。三方都依赖它，它不依赖任何一方。
//
// 完整时序（「FDn」指接收方继承的第 n 号 FD，编号含义见 protocol.go）：
//
//  1. P3 → P4  clone(InitArg)：新建 6 个 namespace，经 UseCgroupFD 原子进入 cgroup
//  2. P3 → P4  FD4：StageSpec 帧，随后是输入文件与 stdin 的字节
//  3. P4       挂只读 rootfs、/work tmpfs、/proc、/dev，pivot_root，写入输入
//  4. P4 → P3  FD3：workspace 事件，用 SCM_RIGHTS 附带 /work 的目录 FD
//  5. P4 → P5  exec /.sandbox/launcher ExecArg
//  6. P4 → P5  FD3：ExecSpec 帧
//  7. P5       rlimit、3 号以上 FD 标 CLOEXEC、降权、安装 seccomp
//  8. P5 → P4  FD4：READY（字节 'R'）
//  9. P4       把自己降到 init 身份，安装 Supervisor 策略
//  10. P4 → P3  FD3：ready 事件（固定字节 InitReadyMessage）
//  11. P3       复查取消、墙钟与 CPU 预算，任一超出就不放行
//  12. P3 → P4  FD3：GO（字节 'G'）
//  13. P4 → P5  FD4：GO（字节 'G'）
//  14. P5       execve 用户程序
//  15. P4 → P3  FD3：exit 事件（退出码、信号，以及从 ExecErrorFD 读到的 P5 失败记录）
//
// 用户程序在第 12 步之前一条指令都不会执行；P4 只能转发 GO，不能自行放行。
// P4 任何一步失败都发 error 事件后退出；P5 任何一步失败都向 ExecErrorFD 写 8 字节记录
// （阶段 + errno）后以 FailureExitCode 退出，execve 成功时该 FD 因 CLOEXEC 关闭，P4 只读到 EOF。
//
// P4 的 FD3（控制通道）是 seqpacket socket，本身保留消息边界，事件直接写 JSON，不需要
// 长度前缀；StageSpec 与 ExecSpec 走管道（P4 的 FD4、P5 的 FD3），沿用 hostexec 的
// 「长度前缀 + JSON」帧。
//
// 文件：protocol（常量与消息类型）、channel（FD3 上收发事件与附带 FD）。
package startup
