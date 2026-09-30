// Package judge 是判题服务的装配入口：在本进程内装配执行层、编排判题、暴露 HTTP 接口、维护节点注册。
//
// 引用边界：
//
//   - cmd/judge 只调用 Run，服务的装配顺序留在本包，不渗进入口文件。
//   - 只有本包与 judge/config 可以直接引用执行层（execution）；判题编排、比对、节点等包只面对
//     自己声明的窄接口。这条边界由模块根 layout_test.go 的 TestOnlyJudgeAssemblyImportsExecution 守住。
//   - 与执行层共享的只有模块顶层 internal/contract 定义的请求、结果与限额类型。
package judge
