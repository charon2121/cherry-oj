// Package sandbox 是对 judge 提供沙箱执行的服务门面：负责 HTTP 协议、blob 存储、排队、
// 限额归一化，以及把执行事实归类成 Status。
//
// 隔离本身不在这里做：本服务以非特权身份运行，namespace、cgroup、降权与 seccomp 全部由
// isolator 完成（linux 后端）。devhost 后端不提供任何隔离，只用于开发调试。
//
// 引用边界：
//
//   - 实现细节全部位于 sandbox/internal/，只有本子树可以引用；cmd/sandbox 只能调用 Run。
//   - 本子树不得引用 isolator——非特权进程不能链接特权服务端的实现。
//     该约束由 isolator 包的 TestUnprivilegedBinariesDoNotLinkIsolator 检查二进制依赖来强制。
//   - 与 isolator 之间只共享 internal/hostexec 定义的本机执行协议，客户端实现在
//     internal/hostexec/client。
//   - 本服务不理解判题：verdict、编译与比对都在 judge。
package sandbox
