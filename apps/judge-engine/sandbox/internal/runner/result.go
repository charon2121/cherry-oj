package runner

import (
	"context"
	"errors"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/sandbox/internal/backend"
	"cherry-oj/judge-engine/sandbox/internal/store"
)

func failedRun(ctx context.Context, status contract.Status, err error) contract.RunResult {
	if ctx.Err() != nil {
		status = contract.StatusInternalError
		err = errors.Join(err, ctx.Err())
	}
	return contract.RunResult{Status: status, Error: err.Error()}
}

func executionResult(ctx context.Context, limits contract.Limits, facts backend.Facts,
	execErr error, stdout, stderr *capWriter) contract.RunResult {
	result := contract.RunResult{ExitCode: facts.ExitCode, Signal: facts.Signal,
		CPUNs: facts.CPUNs, ClockNs: facts.ClockNs, MemoryBytes: facts.MemoryBytes,
		Stdout: stdout.buf.String(), Stderr: stderr.buf.String()}
	// 溢出会主动取消 runCtx；用原始 ctx 判定请求取消，避免把 OLE 误报为平台错误。
	result.Status = classify(limits, facts, stdout.overflow || stderr.overflow, ctx.Err())
	if execErr != nil {
		result.Status = contract.StatusInternalError
		result.Error = execErr.Error()
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

// classify 优先保留取消/平台故障，其次使用后端记录的终止原因。
// cgroup 内存结论依赖本任务 OOM 证据，不能仅凭 SIGKILL 或峰值猜测超限；
// 峰值比较只用于没有组计量能力的 devhost 后端。
func classify(lim contract.Limits, f backend.Facts, outOverflow bool, ctxErr error) contract.Status {
	switch {
	case ctxErr != nil || f.Reason == hostexec.ReasonPlatform:
		return contract.StatusInternalError
	case f.OOMKilled:
		return contract.StatusMemoryLimitExceeded
	case f.Reason == hostexec.ReasonCPU || f.Reason == hostexec.ReasonWall:
		return contract.StatusTimeLimitExceeded
	case outOverflow || f.Reason == hostexec.ReasonOutput:
		return contract.StatusOutputLimitExceeded
	case f.Reason != "":
		return contract.StatusInternalError
	case f.CPUNs > lim.CPUNs:
		return contract.StatusTimeLimitExceeded
	case !f.GroupAccounting && f.MemoryBytes > lim.MemoryBytes:
		return contract.StatusMemoryLimitExceeded
	case f.Signal != 0:
		return contract.StatusSignalled
	case f.ExitCode == 0:
		return contract.StatusOK
	default:
		return contract.StatusNonzeroExit
	}
}
