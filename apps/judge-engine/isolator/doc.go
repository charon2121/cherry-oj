//go:build linux && amd64

// Package isolator 是本机特权执行边界：建立隔离环境、执行一条命令、回收，只返回进程与资源事实。
//
// 同一个二进制（cmd/isolator）在三种进程里扮演三个角色，main 按命令行参数分流：
//
//	P3  daemon + execution  常驻，root。接 sandbox 的连接，每个连接执行一次命令
//	P4  initproc            每次执行新建，新 PID namespace 内的 PID 1；准备文件系统、监督 P5
//	P5  execstage           每次执行新建；降权、装 seccomp，收到 GO 后 execve 成用户程序
//
// 三者之间的 FD 约定与握手时序在 startup 的包文档里。降权步骤在 privilege，
// 过滤策略在 seccomp，资源组在 cgroup。一次执行从头到尾的顺序见 execution 的包文档。
//
// 为什么是三个进程而不是一个：Go 的常驻进程是多线程的，不能在自己身上安全地切换
// namespace、降权、装 seccomp，所以交给新启动的 P4；P4 必须留在 namespace 里当 PID 1
// 回收孤儿、上报退出，不能自己变成用户程序，所以再启动 P5 去 execve。
//
// 引用边界：
//
//   - 非特权的 sandbox 与 judge 不得链接本子树的任何包。子树不放在 internal/ 下，
//     这条边界由本包的 TestUnprivilegedBinariesDoNotLinkIsolator 检查二进制的完整依赖来守住。
//   - 与 sandbox 之间只共享 internal/hostexec 定义的本机执行协议。
//   - 本服务完全不理解判题：编译、比对与 verdict 都在 judge。这条守不住，整个分层就没有意义。
package isolator
