package flow_test

import (
	"context"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/flow"
)

func TestJudgeChecksExecutionWallAgainstCallTimeoutBeforeUpload(t *testing.T) {
	for _, explicit := range []bool{false, true} {
		for _, delta := range []int64{-1, 0, 1} {
			cfg := judgeConfig()
			cfg.ClockRatio = 1
			req := oneCaseRequest("cpp")
			wall := int64(cfg.SandboxTimeout) + delta
			if explicit {
				req.Limits.ClockNs = wall
			} else {
				req.Limits.CPUNs = wall
			}
			fake := &fakeSandbox{runs: []runReply{compileOK(), runOK("answer\n")}}
			result := flow.Judge(context.Background(), fake, cfg, req)
			if delta < 0 {
				if result.Verdict != contract.VerdictAC || len(fake.calls) != 2 {
					t.Fatalf("valid wall rejected (explicit=%v): %+v", explicit, result)
				}
				continue
			}
			if result.Verdict != contract.VerdictSE || !strings.Contains(result.Message, "judge.sandboxTimeout") || !strings.Contains(result.Message, "clockNs") {
				t.Fatalf("budget conflict not explained (explicit=%v delta=%d): %+v", explicit, delta, result)
			}
			if len(fake.uploaded) != 0 || len(fake.calls) != 0 {
				t.Fatal("conflicting request reached sandbox")
			}
		}
	}
}
