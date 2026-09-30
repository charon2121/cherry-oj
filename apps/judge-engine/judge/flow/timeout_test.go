package flow_test

import (
	"context"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/flow"
)

// 请求推出的墙钟（显式值或 cpuNs × clockRatio）超过执行层硬界时，执行器必然拒绝；
// 必须在上传源码前就以 SE 说清楚，而不是让它变成一次莫名的平台错误。
func TestJudgeChecksExecutionWallAgainstHardLimitBeforeUpload(t *testing.T) {
	for _, explicit := range []bool{false, true} {
		for _, delta := range []int64{-1, 0, 1} {
			cfg := judgeConfig()
			cfg.ClockRatio = 1
			req := oneCaseRequest("cpp")
			wall := config.MaxClockNs + delta
			if explicit {
				req.Limits.ClockNs = wall
			} else {
				req.Limits.CPUNs = wall
			}
			fake := &fakeSandbox{runs: []runReply{compileOK(), runOK("answer\n")}}
			result := flow.Judge(context.Background(), fake, cfg, req, nil)
			if delta <= 0 {
				if result.Verdict != contract.VerdictAC || len(fake.calls) != 2 {
					t.Fatalf("valid wall rejected (explicit=%v): %+v", explicit, result)
				}
				continue
			}
			if result.Verdict != contract.VerdictSE || !strings.Contains(result.Message, "wall-clock hard limit") || !strings.Contains(result.Message, "clockNs") {
				t.Fatalf("budget conflict not explained (explicit=%v delta=%d): %+v", explicit, delta, result)
			}
			if len(fake.uploaded) != 0 || len(fake.calls) != 0 {
				t.Fatal("conflicting request reached sandbox")
			}
		}
	}
}
