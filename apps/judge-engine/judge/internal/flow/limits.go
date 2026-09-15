package flow

import (
	"fmt"
	"math"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/config"
)

func compileLimits(cfg config.Settings) contract.Limits {
	return contract.Limits{
		CPUNs:          cfg.Compile.CPUNs,
		ClockNs:        cfg.Compile.ClockNs,
		MemoryBytes:    cfg.Compile.MemoryBytes,
		StdoutMaxBytes: cfg.Output.StdoutMaxBytes,
		StderrMaxBytes: cfg.Output.StderrMaxBytes,
	}
}

func effectiveClockNs(limits contract.JudgeLimits, ratio int64) (int64, error) {
	if limits.ClockNs > 0 {
		return limits.ClockNs, nil
	}
	if ratio <= 0 {
		return 0, fmt.Errorf("clock ratio must be positive, got %d", ratio)
	}
	if limits.CPUNs > math.MaxInt64/ratio {
		return 0, fmt.Errorf("cpuNs %d multiplied by clock ratio %d overflows int64", limits.CPUNs, ratio)
	}
	return limits.CPUNs * ratio, nil
}
