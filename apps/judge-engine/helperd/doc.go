//go:build linux && amd64

// Package helperd 是本机特权执行边界：它建立隔离环境并执行一条命令，只返回进程与资源事实。
//
// 它以 root 运行，是三个服务中唯一持有特权的一个。
//
// 引用边界：
//
//   - 实现细节全部位于 helperd/internal/，只有本子树可以引用；cmd/sandbox-helper 只能调用
//     Dispatch、LoadConfig 与 Run。非特权的 sandbox 服务在编译期就够不到这里，
//     由 Go 的 internal 可见性规则强制，不依赖约定。
//   - 与 sandbox 之间只共享 internal/hostexec 定义的本机执行协议。
//   - 本服务完全不理解判题：编译、比对与 verdict 都在 judge。这条守不住，整个分层就没有意义。
package helperd
