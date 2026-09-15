// Package sandbox 是执行服务的装配入口：接收执行请求、限制容量、把命令交给隔离后端。
//
// 本服务以非特权身份运行，建立隔离环境所需的特权操作全部由 helperd 持有。
//
// 引用边界：
//
//   - 实现细节全部位于 sandbox/internal/，只有本子树可以引用；cmd/sandbox 只能调用 Run。
//   - 本子树不得引用 helperd/internal——非特权进程不能链接特权服务端的实现。
//     该约束由 Go 的 internal 可见性规则在编译期强制，不依赖约定。
//   - 与 helperd 之间只共享 internal/hostexec 定义的本机执行协议，客户端实现在
//     internal/hostexec/client。
//   - 本服务不理解判题：verdict、编译与比对都在 judge。
package sandbox
