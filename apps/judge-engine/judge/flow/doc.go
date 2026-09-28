// Package flow 编排一次判题：上传源码、编译、逐点运行、比对输出、汇总 Verdict、清理引用。
// sandbox 只报告命令执行事实（Status），Verdict 全部在这里产生。
//
// 顺序固定且串行：prepare（校验、加载测试点、算墙钟、上传源码）→ compile（解释型语言跳过；
// 超时、非零退出等判 CE，平台问题判 SE）→ 对每个测试点 runCase（前面的点错了也照跑）→
// evalCase（OK 时用 checker 比对）→ 取最严重的结论 → close（用不随请求取消的上下文删 blob）。
//
// 文件：flow（Judge 与准备、清理）、compile、case（单点运行与 stdin）、result（Status 到
// Verdict 与汇总）、limits（编译限额与墙钟）。
package flow
