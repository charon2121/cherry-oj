//go:build linux && amd64

package execution

import (
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
)

func testRequest() hostexec.Request {
	return hostexec.Request{Version: 1, Command: []string{"main"}, Limits: contract.ExplicitLimits(contract.Limits{CPUNs: 1e9, ClockNs: 2e9, MemoryBytes: 64 << 20, MaxProcesses: 32, StdoutMaxBytes: 1024, StderrMaxBytes: 1024})}
}
