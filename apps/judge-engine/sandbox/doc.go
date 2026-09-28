// Package sandbox 是对 judge 提供沙箱执行的服务门面：负责 HTTP 协议、blob 存储、排队、
// 限额归一化，以及把执行事实归类成 Status。
//
// 隔离本身不在这里做：本服务以非特权身份运行，namespace、cgroup、降权与 seccomp 全部由
// isolator 完成（linux 后端）。devhost 后端不提供任何隔离，只用于开发调试。
//
// 引用边界：
//
//   - cmd/sandbox 只调用 Run，服务的装配顺序留在本包，不渗进入口文件。
//   - 不得引用 judge（sandbox 不理解判题）和特权的 isolator（非特权进程不能链接特权实现）。
//     子树不放在 internal/ 下，这条边界由模块根 layout_test.go 的
//     TestServiceBinariesLinkOnlyTheirOwnSubtree 检查 sandbox 二进制的完整依赖来守住。
//   - 与 isolator 之间只共享 internal/hostexec 定义的本机执行协议，客户端实现在
//     internal/hostexec/client。
//   - 本服务不理解判题：verdict、编译与比对都在 judge。
package sandbox
