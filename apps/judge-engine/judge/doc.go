// Package judge 是判题服务的装配入口：编排一次判题、暴露 HTTP 接口、维护节点注册。
//
// 引用边界：
//
//   - 实现细节全部位于 judge/internal/，只有本子树可以引用；cmd/judge 只能调用 Run。
//   - 本子树不得引用 sandbox/internal 或 helperd/internal——judge 只能通过 HTTP 使用 sandbox。
//     该约束由 Go 的 internal 可见性规则在编译期强制，不依赖约定。
//   - 与 sandbox 共享的只有 internal/contract 定义的跨进程 DTO。
package judge
