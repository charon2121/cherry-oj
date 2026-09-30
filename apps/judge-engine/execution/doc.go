// Package execution 是 judge 进程内的执行层：blob 存储、排队与并发、限额归一化、执行后端，
// 以及把执行事实归类成 Status。入口是 Engine（engine.go）。
//
// 隔离本身不在这里做：judge 以非特权身份运行，namespace、cgroup、降权与 seccomp 全部由
// 每次执行调用一次的 setuid-root C 执行器（apps/sandbox）完成（linux 后端）。devhost 后端
// 不提供任何隔离，只用于开发调试。
//
// 引用边界：
//
//   - 不得引用 judge（执行层不理解判题）。judge 里也只有装配处能直接引用本包；两条都由模块根
//     layout_test.go 的 TestOnlyJudgeAssemblyImportsExecution 守住。
//   - 与执行器之间只有 box 目录约定与一行 JSON 事实（见 apps/sandbox/README.md），
//     调用方在 backend/executor.go。
//   - verdict、编译与比对都在 judge；这里只报告「这条命令执行完了、用了多少资源」。
package execution
