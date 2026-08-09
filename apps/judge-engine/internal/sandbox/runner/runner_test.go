package runner_test

import (
	"context"
	"io"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/container"
	"cherry-oj/judge-engine/internal/sandbox/runner"
	"cherry-oj/judge-engine/internal/sandbox/store"
)

func setup(t *testing.T) (container.Container, store.Store) {
	t.Helper()
	c, err := container.NewHost()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { c.Close() })
	st, err := store.NewDiskStore()
	if err != nil {
		t.Fatal(err)
	}
	return c, st
}

func TestEcho(t *testing.T) {
	c, st := setup(t)
	res := runner.Run(context.Background(), c, st, contract.RunSpec{
		Command: []string{"/bin/echo", "hello"},
		Limits: contract.Limits{
			ClockNs:        int64(2 * time.Second),
			StdoutMaxBytes: 65536,
			StderrMaxBytes: 65536,
		},
	})
	if res.Status != contract.StatusOK {
		t.Fatalf("status=%s err=%s", res.Status, res.Error)
	}
	if !strings.Contains(res.Stdout, "hello") {
		t.Fatalf("stdout=%q", res.Stdout)
	}
}

func TestTimeout(t *testing.T) {
	c, st := setup(t)
	start := time.Now()
	res := runner.Run(context.Background(), c, st, contract.RunSpec{
		Command: []string{"/bin/sleep", "5"},
		Limits: contract.Limits{
			ClockNs:        int64(500 * time.Millisecond),
			StdoutMaxBytes: 65536,
			StderrMaxBytes: 65536,
		},
	})
	elapsed := time.Since(start)
	if res.Status != contract.StatusTimeLimitExceeded {
		t.Fatalf("status=%s err=%s", res.Status, res.Error)
	}
	if elapsed >= 2*time.Second {
		t.Fatalf("took %v, expected kill well before sleep 5 finishes", elapsed)
	}
}

func TestNonzero(t *testing.T) {
	c, st := setup(t)
	res := runner.Run(context.Background(), c, st, contract.RunSpec{
		Command: []string{"/bin/sh", "-c", "exit 3"},
		Limits: contract.Limits{
			ClockNs:        int64(2 * time.Second),
			StdoutMaxBytes: 65536,
			StderrMaxBytes: 65536,
		},
	})
	if res.Status != contract.StatusNonzeroExit {
		t.Fatalf("status=%s err=%s", res.Status, res.Error)
	}
	if res.ExitCode != 3 {
		t.Fatalf("exitCode=%d want 3", res.ExitCode)
	}
}

// Limits 全零 = 不限时、不限输出，不该被当成「限制为 0」
func TestZeroLimits(t *testing.T) {
	c, st := setup(t)
	res := runner.Run(context.Background(), c, st, contract.RunSpec{
		Command: []string{"/bin/echo", "hi"},
		Limits:  contract.Limits{},
	})
	if res.Status != contract.StatusOK {
		t.Fatalf("status=%s err=%s", res.Status, res.Error) // 没 guard 时是 TimeLimitExceeded
	}
	if !strings.Contains(res.Stdout, "hi") {
		t.Fatalf("stdout=%q", res.Stdout) // 没 guard 时是空的
	}
}

// 输出真的超限时要截断并报 OLE
func TestOutputLimitExceeded(t *testing.T) {
	c, st := setup(t)
	res := runner.Run(context.Background(), c, st, contract.RunSpec{
		Command: []string{"/bin/sh", "-c", "printf '0123456789'"},
		Limits: contract.Limits{
			ClockNs:        int64(2 * time.Second),
			StdoutMaxBytes: 4,
			StderrMaxBytes: 65536,
		},
	})
	if res.Status != contract.StatusOutputLimitExceeded {
		t.Fatalf("status=%s want OutputLimitExceeded", res.Status)
	}
	if res.Stdout != "0123" {
		t.Fatalf("stdout=%q want %q", res.Stdout, "0123")
	}
}

func TestArtifacts(t *testing.T) {
	c, st := setup(t)
	const body = "artifact-body"
	res := runner.Run(context.Background(), c, st, contract.RunSpec{
		Command: []string{"/bin/cp", "in", "out"},
		Inputs: map[string]contract.FileSource{
			"in": {Text: body},
		},
		Artifacts: []string{"out"},
		Limits: contract.Limits{
			ClockNs:        int64(2 * time.Second),
			StdoutMaxBytes: 65536,
			StderrMaxBytes: 65536,
		},
	})
	if res.Status != contract.StatusOK {
		t.Fatalf("status=%s err=%s", res.Status, res.Error)
	}
	ref, ok := res.Artifacts["out"]
	if !ok || ref == "" {
		t.Fatalf("Artifacts[out]=%q", ref)
	}
	rc, err := st.Get(ref)
	if err != nil {
		t.Fatalf("store.Get(%q): %v", ref, err)
	}
	defer rc.Close()
	got, err := io.ReadAll(rc)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != body {
		t.Fatalf("artifact=%q want %q", got, body)
	}
}
