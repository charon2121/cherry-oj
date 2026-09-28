package judge

import (
	"bytes"
	"context"
	"errors"
	"io"
	"log/slog"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	judgeconfig "cherry-oj/judge-engine/judge/internal/config"
)

type unreachableSandbox struct{}

func (unreachableSandbox) Upload(context.Context, io.Reader) (string, error) {
	return "", errors.New("connection refused")
}
func (unreachableSandbox) Run(context.Context, contract.RunSpec) (contract.RunResult, error) {
	return contract.RunResult{}, errors.New("connection refused")
}
func (unreachableSandbox) Delete(context.Context, string) error { return nil }

// SE 的原因只写在响应里；judge 必须自己留痕，否则排查只能去调用方翻响应。
func TestSystemErrorIsLoggedWithReason(t *testing.T) {
	var logs bytes.Buffer
	s := &judgeService{sandbox: unreachableSandbox{}, config: judgeconfig.Default().Judge,
		logger: slog.New(slog.NewJSONHandler(&logs, nil))}
	req := contract.JudgeRequest{SubmissionID: "s-7", LanguageID: "python", Source: "print(1)",
		Mode: contract.ModeTrial, Limits: contract.JudgeLimits{CPUNs: 1e9, MemoryBytes: 64 << 20},
		Cases: []contract.CaseSpec{{Input: "1\n", Expected: "1\n"}}}
	if result := s.Judge(context.Background(), req); result.Verdict != contract.VerdictSE {
		t.Fatalf("sandbox 不可用应判 SE: %+v", result)
	}
	out := logs.String()
	if !strings.Contains(out, `"msg":"judge.result.system_error"`) || !strings.Contains(out, "s-7") || !strings.Contains(out, "connection refused") {
		t.Fatalf("SE 没有带原因留痕: %s", out)
	}
}
