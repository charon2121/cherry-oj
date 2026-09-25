//go:build linux && amd64

// Package isolator 是本机特权执行边界：它建立隔离环境并执行一条命令，只返回进程与资源事实。
//
// 它以 root 运行，是三个服务中唯一持有特权的一个。
//
// 引用边界：
//
//   - 非特权的 sandbox 与 judge 不得链接本子树的任何包。子树不放在 internal/ 下，
//     这条边界由本包的 TestUnprivilegedBinariesDoNotLinkIsolator 检查二进制的完整依赖来守住。
//   - 与 sandbox 之间只共享 internal/hostexec 定义的本机执行协议。
//   - 本服务完全不理解判题：编译、比对与 verdict 都在 judge。这条守不住，整个分层就没有意义。
package isolator
