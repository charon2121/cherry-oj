// Package api 是 sandbox 对 judge 的 HTTP 入口：POST /run 执行一条命令，/blobs 存取和删除文件。
//
// 本包只判断「这次对话成不成」：JSON 解不开、有未知字段或尾随内容、限额不合法、命令为空
// 返回 400；并发或队列已满返回 503。命令本身超时、非零退出、被信号杀死都是 200，
// 结论写在 RunResult.Status 里。整个 HTTP 服务同时最多处理 Options.MaxConcurrent 个请求，
// 超出立即拒绝，排队只发生在 pool 里。
package api
