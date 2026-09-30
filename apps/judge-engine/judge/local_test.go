package judge

import (
	"context"
	"io"
	"log/slog"
	"net"
	"net/http"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/execution/backend"
	"cherry-oj/judge-engine/internal/contract"
	judgeconfig "cherry-oj/judge-engine/judge/config"
)

func quietLogger() *slog.Logger { return slog.New(slog.NewTextHandler(io.Discard, nil)) }

// local 模式在本进程内装配执行层：同一份判题编排不经 HTTP 也能判出结论。
func TestLocalModeJudgesInProcess(t *testing.T) {
	cfg := judgeconfig.Default()
	cfg.Judge.SandboxMode = judgeconfig.SandboxModeLocal
	cfg.Execution.Backend, cfg.Execution.AllowUnsafeBackend = backend.NameDevHost, true
	cfg.Execution.Store.Root = filepath.Join(t.TempDir(), "blobs")
	if err := cfg.Validate(); err != nil {
		t.Fatal(err)
	}
	sb, stopped, closeSandbox, err := openSandbox(cfg, quietLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer closeSandbox()
	if stopped == nil {
		t.Fatal("local mode must expose when the execution layer stops accepting work")
	}
	if v, _ := sb.Version(context.Background()); v.Isolation != backend.NameDevHost {
		t.Fatalf("version = %+v", v)
	}
	s := &judgeService{sandbox: sb, config: cfg.Judge, logger: quietLogger()}
	req := contract.JudgeRequest{SubmissionID: "s-local", LanguageID: "python", Source: "print(input())",
		Mode: contract.ModeTrial, Limits: contract.JudgeLimits{CPUNs: 2e9, MemoryBytes: 256 << 20},
		Cases: []contract.CaseSpec{{Input: "7\n", Expected: "7\n"}}}
	if result := s.Judge(context.Background(), req); result.Verdict != contract.VerdictAC {
		t.Fatalf("result = %+v", result)
	}
}

// 执行层停止接单（回收未确认）时 judge 必须以失败退出，而不是继续在线把每次提交判成 SE。
func TestStoppedExecutionEndsServe(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	stopped := make(chan struct{})
	close(stopped)
	done := make(chan error, 1)
	go func() { done <- serve(context.Background(), &http.Server{}, listener, nil, stopped, quietLogger()) }()
	select {
	case err := <-done:
		if err == nil || !strings.Contains(err.Error(), "reclaim was not confirmed") {
			t.Fatalf("serve error = %v", err)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("serve kept running after the execution layer stopped")
	}
}

// 模式必须是明确的两个值之一；local 模式要校验执行层配置，http 模式不需要。
func TestSandboxModeValidation(t *testing.T) {
	cfg := judgeconfig.Default()
	cfg.Judge.SandboxMode = "auto"
	if err := cfg.Validate(); err == nil {
		t.Fatal("unknown mode accepted")
	}
	cfg = judgeconfig.Default()
	cfg.Judge.SandboxMode = judgeconfig.SandboxModeLocal
	cfg.Execution.Backend = backend.NameDevHost
	if err := cfg.Validate(); err == nil || !strings.Contains(err.Error(), "execution.allowUnsafeBackend") {
		t.Fatalf("local mode skipped execution validation: %v", err)
	}
	cfg.Judge.SandboxMode = judgeconfig.SandboxModeHTTP
	if err := cfg.Validate(); err != nil {
		t.Fatalf("http mode must not depend on the execution section: %v", err)
	}
}
