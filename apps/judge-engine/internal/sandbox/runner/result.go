package runner

import (
	"context"
	"errors"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/container"
	"cherry-oj/judge-engine/internal/sandbox/store"
)

// MaxInlineBytes 限制一次响应内所有内联产物的累计大小。
const MaxInlineBytes int64 = 1 << 20

func failedRun(ctx context.Context, status contract.Status, err error) contract.RunResult {
	if ctx.Err() != nil {
		status = contract.StatusInternalError
		err = errors.Join(err, ctx.Err())
	}
	return contract.RunResult{Status: status, Error: err.Error()}
}

func executionResult(ctx context.Context, limits contract.Limits, usage container.Usage, waitErr error, stdout, stderr *capWriter) contract.RunResult {
	result := contract.RunResult{ExitCode: usage.ExitCode, Signal: usage.Signal, CPUNs: usage.CPUNs, ClockNs: usage.ClockNs, MemoryBytes: usage.MemoryBytes, Stdout: stdout.buf.String(), Stderr: stderr.buf.String()}
	// 溢出会主动取消 runCtx；用原始 ctx 判定请求取消，避免把 OLE 误报为平台错误。
	result.Status = classify(limits, usage, stdout.overflow || stderr.overflow, ctx.Err())
	if waitErr != nil {
		result.Status = contract.StatusInternalError
		result.Error = waitErr.Error()
	}
	return result
}

func rejectArtifacts(st store.Store, result contract.RunResult, err error) contract.RunResult {
	for _, ref := range result.Artifacts {
		err = errors.Join(err, st.Delete(ref))
	}
	result.Outputs = nil
	result.Artifacts = nil
	result.Status = contract.StatusInternalError
	if result.Error != "" {
		err = errors.Join(errors.New(result.Error), err)
	}
	result.Error = err.Error()
	return result
}

// classify 优先保留取消/平台故障，其次使用 helper 记录的终止原因。
// cgroup 内存结论依赖本任务 OOM 证据，不能仅凭 SIGKILL 或峰值猜测超限；
// 峰值比较只用于没有组计量能力的 host 后端。
func classify(lim contract.Limits, u container.Usage, outOverflow bool, ctxErr error) contract.Status {
	switch {
	case ctxErr != nil || u.Reason == container.ReasonPlatform:
		return contract.StatusInternalError
	case u.OOMKilled:
		return contract.StatusMemoryLimitExceeded
	case u.Reason == container.ReasonCPU || u.Reason == container.ReasonWall:
		return contract.StatusTimeLimitExceeded
	case outOverflow || u.Reason == container.ReasonOutput:
		return contract.StatusOutputLimitExceeded
	case u.Reason != "":
		return contract.StatusInternalError
	case u.CPUNs > lim.CPUNs:
		return contract.StatusTimeLimitExceeded
	case !u.GroupAccounting && u.MemoryBytes > lim.MemoryBytes:
		return contract.StatusMemoryLimitExceeded
	case u.Signal != 0:
		return contract.StatusSignalled
	case u.ExitCode == 0:
		return contract.StatusOK
	default:
		return contract.StatusNonzeroExit
	}
}
