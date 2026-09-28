package runner

import (
	"context"
	"fmt"
	"time"

	"cherry-oj/judge-engine/internal/contract"
)

const (
	maxFilesPerRun = 128
	maxStdoutBytes = 64 << 20
	maxStderrBytes = 16 << 20
)

const (
	defaultCPUNs        = int64(time.Second)
	defaultClockNs      = int64(5 * time.Second)
	defaultMemoryBytes  = 128 << 20
	defaultMaxProcesses = 64
	defaultStdoutBytes  = 64 << 10
	defaultStderrBytes  = 64 << 10
)

func defaultLimits() contract.Limits {
	return contract.ExplicitLimits(contract.Limits{
		CPUNs: defaultCPUNs, ClockNs: defaultClockNs,
		MemoryBytes: defaultMemoryBytes, MaxProcesses: defaultMaxProcesses,
		StdoutMaxBytes: defaultStdoutBytes, StderrMaxBytes: defaultStderrBytes,
	})
}

// validateRequest 保留显式零预算的执行结论；只有 nil rejection 才能继续准备输入。
func validateRequest(ctx context.Context, spec contract.RunSpec) (contract.Limits, *contract.RunResult) {
	reject := func(status contract.Status, err error) *contract.RunResult {
		result := failedRun(ctx, status, err)
		return &result
	}
	limits, err := spec.Limits.WithDefaults(defaultLimits())
	if err != nil {
		return limits, reject(contract.StatusInternalError, err)
	}
	if len(spec.Command) == 0 || len(spec.Inputs) > maxFilesPerRun || len(spec.Outputs)+len(spec.Artifacts) > maxFilesPerRun {
		return limits, reject(contract.StatusWorkspaceError, fmt.Errorf("invalid command or file count"))
	}
	if err := ctx.Err(); err != nil {
		return limits, reject(contract.StatusInternalError, err)
	}
	// WithDefaults 已区分缺省和显式零值；零预算不能被后端的默认设置重新放宽。
	if limits.CPUNs == 0 || limits.ClockNs == 0 {
		return limits, &contract.RunResult{Status: contract.StatusTimeLimitExceeded}
	}
	if limits.MemoryBytes == 0 {
		return limits, &contract.RunResult{Status: contract.StatusMemoryLimitExceeded}
	}
	if limits.MaxProcesses == 0 {
		return limits, reject(contract.StatusInternalError, fmt.Errorf("maxProcesses=0 cannot start anything"))
	}
	// 所有后端都限制服务侧内存；Linux更严格的硬界由适配器校验，绝不悄悄截小预算。
	if limits.StdoutMaxBytes > maxStdoutBytes || limits.StderrMaxBytes > maxStderrBytes {
		return limits, reject(contract.StatusInternalError, fmt.Errorf("the output budget exceeds the service hard boundary"))
	}
	return limits, nil
}

func outputNames(spec contract.RunSpec) []string {
	// 同一文件可以同时内联和持久化，但后端只交付一次。
	outputs := make([]string, 0, len(spec.Outputs)+len(spec.Artifacts))
	seen := map[string]bool{}
	for _, list := range [][]string{spec.Outputs, spec.Artifacts} {
		for _, name := range list {
			if !seen[name] {
				outputs = append(outputs, name)
				seen[name] = true
			}
		}
	}
	return outputs
}
