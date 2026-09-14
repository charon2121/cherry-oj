// Package runner 解析文件引用、编排单次执行并发布已确认的产物；不持有特权操作。
package runner

import (
	"context"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/container"
	"cherry-oj/judge-engine/internal/sandbox/store"
)

// Run 借用 c 和 st；Container 的最终关闭由 Pool 负责。
// 产物在输入关闭、Container 回收都成功后才能对外发布。
func Run(ctx context.Context, c container.Container, st store.Store, spec contract.RunSpec) contract.RunResult {
	limits, rejection := validateRequest(ctx, spec)
	if rejection != nil {
		return *rejection
	}
	input, err := prepareInputs(ctx, c, st, spec)
	if err != nil {
		return failedRun(ctx, contract.StatusWorkspaceError, err)
	}
	// 正常路径在返回前检查关闭错误；defer 只兜底 Start/Wait 等实现发生 panic 的情况。
	defer input.close()
	runCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	stdout, stderr := newCapWriter(limits.StdoutMaxBytes), newCapWriter(limits.StderrMaxBytes)
	stdout.onOverflow, stderr.onOverflow = cancel, cancel

	proc, startErr := c.Start(runCtx, container.Spec{
		Command: spec.Command, Env: spec.Env, Stdin: input.reader,
		Stdout: stdout, Stderr: stderr, Limits: limits, Outputs: outputNames(spec),
	})
	var result contract.RunResult
	if startErr != nil {
		result = failedRun(ctx, contract.StatusInternalError, startErr)
	} else {
		usage, waitErr := proc.Wait(runCtx)
		result = executionResult(ctx, limits, usage, waitErr, stdout, stderr)
	}
	if result.Status == contract.StatusOK {
		result = collect(c, st, spec, result)
	}

	cancel()
	// 关闭也是成功条件；此时 Store 中的产物还没有对调用方发布。
	if err := input.close(); err != nil {
		result = rejectArtifacts(st, result, err)
	}
	return result
}
