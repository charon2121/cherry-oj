//go:build linux && amd64

// Package execution 在 P3 内拥有一次执行：建 cgroup、启动 P4、监督、回收，最后得出结论。
//
// 入口是 Run，顺序固定：
//
//	newExecution  固定隔离计划（请求深拷贝 + 槽位身份 + rootfs），开始计时
//	Run           建 cgroup → isolatedProcess.Start：clone P4，送 StageSpec 与输入
//	supervise     等第一个「该停了」的事件：exit、墙钟、CPU 采样、输出超限、取消、启动超时；
//	              收到 ready 时先复查取消、墙钟与 CPU，再发 GO
//	finish        读取消状态 → 停组并取最终计量 → 等待 P4 与 I/O → conclude →
//	              打开产物 → 释放句柄、cgroup 与挂载点
//
// 状态只能按 state.go 的转移表变化；结论由纯函数 conclude 从 executionFacts 推出，不做 I/O。
// 回收无法确认时 Run 返回 error，调用方必须停止接单；命令本身的失败只写进 Result。
//
// 文件：execution（类型与 Run）、isolation_plan、process / process_events / process_wait
// （P3 这一侧的 P4 句柄：启动、协议事件、等待与释放）、supervise、finish、conclusion、state、
// result 与 artifacts（交付）、capture（有界收集输出）、owned_file、usage。
package execution
