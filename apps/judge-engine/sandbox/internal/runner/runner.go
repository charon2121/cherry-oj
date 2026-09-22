// Package runner 解析文件引用、编排单次执行并发布已确认的产物；不持有特权操作。
package runner

import (
	"context"
	"errors"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/internal/backend"
	"cherry-oj/judge-engine/sandbox/internal/store"
)

// Runner 借用可并发调用的后端与 Store；每次 Run 的输入、产物和取消状态仍是局部值。
type Runner struct {
	backend backend.Backend
	store   store.Store
}

func New(b backend.Backend, st store.Store) *Runner { return &Runner{backend: b, store: st} }

// Run 返回的 error 只表示**回收未确认**：这次执行留下的资源无法判定，
// 容量不能归还给新任务。普通的执行失败（超时、非零退出、平台故障）都通过 RunResult.Status
// 表达，error 仍是 nil。
func (r *Runner) Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error) {
	b, st := r.backend, r.store
	limits, rejection := validateRequest(ctx, spec)
	if rejection != nil {
		return *rejection, nil
	}
	src, err := openSources(ctx, st, spec)
	if err != nil {
		return failedRun(ctx, contract.StatusWorkspaceError, err), nil
	}
	// 正常路径在返回前检查关闭错误；defer 只兜底后端实现发生 panic 的情况。
	defer src.close()

	// 输出超限时主动取消，让被执行的程序尽快停下；判定仍用原始 ctx，避免把 OLE 误报为平台错误。
	runCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	stdout, stderr := newCapWriter(limits.StdoutMaxBytes), newCapWriter(limits.StderrMaxBytes)
	stdout.onOverflow, stderr.onOverflow = cancel, cancel

	overflow := func() bool { return stdout.overflow || stderr.overflow }
	collect := newCollector(st, spec, limits, overflow, ctx.Err)
	facts, execErr := b.Execute(runCtx, backend.Job{
		Command: spec.Command, Env: spec.Env, Stdin: src.stdin, Inputs: src.inputs,
		Outputs: outputNames(spec), Limits: limits, Stdout: stdout, Stderr: stderr,
	}, collect.accept)

	result := executionResult(ctx, limits, facts, execErr, stdout, stderr)
	if result.Status == contract.StatusOK {
		result = collect.publish(spec, result)
	} else if err := collect.discard(); err != nil {
		result = rejectArtifacts(st, result, err)
	}

	cancel()
	// 输入关闭也是成功条件；此时 Store 中的产物还没有对调用方发布。
	if err := src.close(); err != nil {
		result = rejectArtifacts(st, result, err)
	}
	// 回收未确认必须让调用方停止接单，不能只体现为一次失败的执行。
	var cleanup *backend.CleanupError
	if errors.As(execErr, &cleanup) {
		return rejectArtifacts(st, result, cleanup), cleanup
	}
	return result, nil
}
