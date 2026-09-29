// Package sandbox 是对 judge 提供沙箱执行的服务门面：负责 HTTP 协议、blob 存储、排队、
// 限额归一化，以及把执行事实归类成 Status。
//
// 隔离本身不在这里做：本服务以非特权身份运行，namespace、cgroup、降权与 seccomp 全部由
// 每次执行调用一次的 setuid-root C 执行器（apps/sandbox）完成（linux 后端）。devhost 后端
// 不提供任何隔离，只用于开发调试。
//
// 引用边界：
//
//   - cmd/sandbox 只调用 Run，服务的装配顺序留在本包，不渗进入口文件。
//   - 不得引用 judge（sandbox 不理解判题）。子树不放在 internal/ 下，这条边界由模块根
//     layout_test.go 的 TestServiceBinariesLinkOnlyTheirOwnSubtree 检查 sandbox 二进制的
//     完整依赖来守住。
//   - 与执行器之间只有 box 目录约定与一行 JSON 事实（见 apps/sandbox/README.md），
//     调用方在 backend/executor.go。
//   - 本服务不理解判题：verdict、编译与比对都在 judge。
package sandbox
