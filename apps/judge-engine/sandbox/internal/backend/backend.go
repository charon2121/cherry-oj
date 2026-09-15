// Package backend 是单次命令执行的可替换边界。
//
// 一次 Execute 就是一次完整执行：铺输入、跑命令、交付产物、回收资源。**返回即代表回收完成**，
// 调用方据此决定是否对外发布结果，不需要再调用什么关闭方法。
//
// 这个形状是刻意的：此前的接口是「放输入 → 启动一次 → 等待 → 取产物 → 关闭」四个阶段，
// 而这个时序只写在运行时错误里（「Container只能执行一次」「工作区不再接受输入」……），
// 两个实现各自维护一组状态字段来守护它。改成一次性调用之后，「只能执行一次」由
// 「不存在可复用对象」保证，那些状态字段随之消失。
//
// 本包不理解判题：它只报告进程与资源事实，verdict 在 judge。
package backend

import (
	"context"
	"fmt"
	"io"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
)

// Source 是一个输入来源。Reader 由调用方持有并负责关闭；Execute 只读取，不接管。
type Source struct {
	Reader     io.Reader
	Executable bool
}

// NamedSource 是写进工作区的输入文件。Name 是工作区内的逻辑路径，不是宿主路径。
type NamedSource struct {
	Name string
	Source
}

// Job 一次性声明整次执行需要的全部信息。
type Job struct {
	Command []string
	Env     []string
	Stdin   *Source
	Inputs  []NamedSource
	// Outputs 声明执行结束后要取回哪些文件，限制后端可交付的范围。
	Outputs []string
	Limits  contract.Limits
	// Stdout/Stderr 由调用方提供带上限的 writer；后端不自行决定截断策略。
	Stdout, Stderr io.Writer
}

// OutputSink 在执行结束、资源回收完成之后逐个交付产物。实现必须把 r 读到结束再返回，
// 不能保留 r 异步读取——Execute 返回后它就不再有效。
//
// facts 随每个产物一并给出，使调用方在交付当场就能判断这次结果值不值得保留：
// 一次超时或非零退出的执行照样可能留下产物，而把它们写进 Store 再删掉既多一次落盘，
// 也可能在容量紧张时把一次超时变成平台错误。
type OutputSink func(facts Facts, name string, r io.Reader) error

// Facts 是一次执行的客观事实，不包含判题结论。
type Facts struct {
	ExitCode    int
	Signal      int
	CPUNs       int64
	MemoryBytes int64
	ClockNs     int64
	// Reason 是主动终止或平台故障的原因；空值表示没有发生主动终止。
	Reason hostexec.Reason
	// OOMKilled 要求本任务的 OOM 证据，仅有 SIGKILL 不够。
	OOMKilled bool
	// GroupAccounting 标识资源来自整组计量，调用方据此避免套用单进程峰值推断。
	GroupAccounting bool
}

// Backend 执行一次命令。实现必须支持并发调用：每次执行独占自己的工作区与资源组。
type Backend interface {
	Execute(ctx context.Context, j Job, sink OutputSink) (Facts, error)
}

// CleanupError 表示这次执行的资源回收没有得到确认。
//
// 它和普通执行失败是两回事：普通失败只让本次执行失败，而回收未确认意味着容量不能归还给
// 新任务——残留的进程或挂载会与后续执行重叠。调用方据此停止接单，而不是继续跑下一条命令。
type CleanupError struct{ Err error }

func (e *CleanupError) Error() string { return "回收未确认: " + e.Err.Error() }
func (e *CleanupError) Unwrap() error { return e.Err }

func cleanupFailed(format string, args ...any) error {
	return &CleanupError{Err: fmt.Errorf(format, args...)}
}
