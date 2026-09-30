package runner_test

import (
	"context"
	"encoding/json"
	"io"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/execution/backend"
	"cherry-oj/judge-engine/execution/runner"
	"cherry-oj/judge-engine/execution/store"
	"cherry-oj/judge-engine/internal/contract"
)

func setup(t *testing.T) (backend.Backend, store.Store) {
	t.Helper()
	c := backend.NewDevHost()
	st, err := store.NewDiskStoreWithRoot(t.TempDir() + "/store")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := st.Close(); err != nil {
			t.Error(err)
		}
	})
	return c, st
}

func TestEcho(t *testing.T) {
	c, st := setup(t)
	res, _ := runner.New(c, st).Run(context.Background(), contract.RunSpec{
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
	res, _ := runner.New(c, st).Run(context.Background(), contract.RunSpec{
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
	res, _ := runner.New(c, st).Run(context.Background(), contract.RunSpec{
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
	res, _ := runner.New(c, st).Run(context.Background(), contract.RunSpec{
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

// 显式的零输出预算是真实的限制，不是「没配置」：不写输出的程序照常结束，写了就是 OLE。
// 显式 0 只能从 JSON 表达（Go 字面量无法区分省略与 0），所以这里走一次解码。
func TestExplicitZeroOutputBudget(t *testing.T) {
	c, st := setup(t)
	var limits contract.Limits
	if err := json.Unmarshal([]byte(`{"clockNs":2000000000,"stdoutMaxBytes":0,"stderrMaxBytes":65536}`), &limits); err != nil {
		t.Fatal(err)
	}
	quiet, _ := runner.New(c, st).Run(context.Background(), contract.RunSpec{Command: []string{"/bin/sh", "-c", "exit 0"}, Limits: limits})
	if quiet.Status != contract.StatusOK {
		t.Fatalf("quiet program: status=%s err=%s", quiet.Status, quiet.Error)
	}
	writer, _ := runner.New(c, st).Run(context.Background(), contract.RunSpec{Command: []string{"/bin/sh", "-c", "printf x"}, Limits: limits})
	if writer.Status != contract.StatusOutputLimitExceeded || writer.Stdout != "" {
		t.Fatalf("writer: status=%s stdout=%q", writer.Status, writer.Stdout)
	}
}

// 输出真的超限时要截断并报 OLE
func TestOutputLimitExceeded(t *testing.T) {
	c, st := setup(t)
	res, _ := runner.New(c, st).Run(context.Background(), contract.RunSpec{
		Command: []string{"/bin/sh", "-c", "printf '0123456789'"},
		Limits: contract.Limits{
			ClockNs:        int64(2 * time.Second),
			StdoutMaxBytes: 4,
			StderrMaxBytes: 65536,
		},
	})
	if res.Status != contract.StatusOutputLimitExceeded {
		t.Fatalf("status=%s error=%s stdout=%q want OutputLimitExceeded", res.Status, res.Error, res.Stdout)
	}
	if res.Stdout != "0123" {
		t.Fatalf("stdout=%q want %q", res.Stdout, "0123")
	}
}

func TestArtifacts(t *testing.T) {
	c, st := setup(t)
	const body = "artifact-body"
	res, _ := runner.New(c, st).Run(context.Background(), contract.RunSpec{
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
