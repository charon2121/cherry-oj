// Package judge 是判题服务的装配入口：编排一次判题、暴露 HTTP 接口、维护节点注册。
//
// 引用边界：
//
//   - cmd/judge 只调用 Run，服务的装配顺序留在本包，不渗进入口文件。
//   - 不得引用 sandbox（judge 只能通过 HTTP 使用它）。子树不放在 internal/ 下，这条边界由
//     模块根 layout_test.go 的 TestServiceBinariesLinkOnlyTheirOwnSubtree 检查 judge 二进制的
//     完整依赖来守住，间接引用也算。
//   - 与 sandbox 共享的只有模块顶层 internal/contract 定义的跨进程 DTO。
package judge
