package backend

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
)

// fakeExecutor 用 shell 脚本扮演 setuid 执行器：脚本拿到 box 目录 $d，按约定写 out/ 并输出一行 JSON。
// 真实执行器的行为由 apps/sandbox 的真实内核测试覆盖，这里只测 Go 侧对约定的遵守。
func fakeExecutor(t *testing.T, script string) (*Executor, string) {
	t.Helper()
	dir := t.TempDir()
	root := filepath.Join(dir, "boxes")
	bin := filepath.Join(dir, "sandbox")
	header := "#!/bin/sh\nd=\"" + root + "/$2\"\n"
	if err := os.WriteFile(bin, []byte(header+script), 0o755); err != nil {
		t.Fatal(err)
	}
	e, err := openExecutor(bin, root, 1)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { e.Close() })
	return e, filepath.Join(root, "0")
}

// factsLine 生成一行执行器事实，stdout/stderr 为空、没有产物。
func factsLine(reason string, cancelled bool, failure string) string {
	return fmt.Sprintf(`{"version":1,"exitCode":0,"signal":0,"cpuNs":5,"memoryBytes":6,"clockNs":7,"reason":%q,`+
		`"oom":0,"oomKill":0,"memoryMaxEvents":0,"pidsMaxEvents":0,"cancelled":%t,"outputExceeded":false,`+
		`"stdoutBytes":0,"stderrBytes":0,"outputs":[],"error":%q}`, reason, cancelled, failure)
}

// printFacts 生成脚本片段：写空的 stdout/stderr，再输出一行事实。
func printFacts(line string) string {
	return ": > \"$d/out/stdout\"; : > \"$d/out/stderr\"\nprintf '%s\\n' '" + line + "'\n"
}

func testJob() Job {
	return Job{Command: []string{"probe", "echo"}, Env: []string{"A=b"}, Outputs: []string{"a.out"},
		Inputs: []NamedSource{{Name: "src/main.cpp", Source: Source{Reader: strings.NewReader("int main(){}")}}},
		Stdin:  &Source{Reader: strings.NewReader("hello")},
		Limits: contract.Limits{CPUNs: 1, ClockNs: 2, MemoryBytes: 3, MaxProcesses: 4, StdoutMaxBytes: 1024, StderrMaxBytes: 1024}}
}

func TestExecutorDeliversFactsStreamsAndArtifacts(t *testing.T) {
	e, box := fakeExecutor(t, `
cp "$d/spec" "$d/../captured-spec"
cat "$d/stdin" > "$d/out/stdout"
printf err > "$d/out/stderr"
cp "$d/in/0" "$d/out/artifact-0"
printf '{"version":1,"exitCode":0,"signal":0,"cpuNs":5,"memoryBytes":6,"clockNs":7,"reason":"","oom":0,"oomKill":0,"memoryMaxEvents":0,"pidsMaxEvents":0,"cancelled":false,"outputExceeded":false,"stdoutBytes":5,"stderrBytes":3,"outputs":[{"index":0,"path":"a.out","sizeBytes":12}],"error":""}\n'
`)
	var stdout, stderr bytes.Buffer
	j := testJob()
	j.Stdout, j.Stderr = &stdout, &stderr
	artifacts := map[string]string{}
	f, err := e.Execute(context.Background(), j, func(_ Facts, name string, r io.Reader) error {
		b, err := io.ReadAll(r)
		artifacts[name] = string(b)
		return err
	})
	if err != nil {
		t.Fatal(err)
	}
	if f.CPUNs != 5 || f.MemoryBytes != 6 || f.ClockNs != 7 || !f.GroupAccounting {
		t.Errorf("facts = %+v", f)
	}
	if stdout.String() != "hello" || stderr.String() != "err" || artifacts["a.out"] != "int main(){}" {
		t.Errorf("stdout=%q stderr=%q artifacts=%v", stdout.String(), stderr.String(), artifacts)
	}
	// box 在交付后被清空，脚本另存了一份请求供这里核对。
	spec, err := os.ReadFile(filepath.Join(box, "..", "captured-spec"))
	if err != nil {
		t.Fatal(err)
	}
	for _, record := range []string{"arg=probe", "arg=echo", "env=A=b", "input=0:src/main.cpp", "output=a.out",
		"cpu_ns=1", "clock_ns=2", "memory_bytes=3", "max_processes=4", "stdout_max_bytes=1024", "stderr_max_bytes=1024"} {
		if !bytes.Contains(spec, []byte(record+"\x00")) {
			t.Errorf("spec is missing %q: %q", record, spec)
		}
	}
}

func TestExecutorUnreclaimedBoxIsNotReused(t *testing.T) {
	e, _ := fakeExecutor(t, "echo stuck >&2; exit 2\n")
	_, err := e.Execute(context.Background(), testJob(), nil)
	var cleanup *CleanupError
	if !errors.As(err, &cleanup) {
		t.Fatalf("err = %v, want CleanupError", err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()
	if _, err := e.Execute(ctx, testJob(), nil); !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("an unreclaimed box was handed out again: %v", err)
	}
}

func TestExecutorKilledBySignalIsUnreclaimed(t *testing.T) {
	e, _ := fakeExecutor(t, "kill -9 $$\n")
	var cleanup *CleanupError
	if _, err := e.Execute(context.Background(), testJob(), nil); !errors.As(err, &cleanup) {
		t.Fatalf("err = %v, want CleanupError", err)
	}
}

func TestExecutorRefusalReturnsTheBox(t *testing.T) {
	e, _ := fakeExecutor(t, "echo 'box 0 is busy' >&2; exit 1\n")
	for i := 0; i < 2; i++ {
		_, err := e.Execute(context.Background(), testJob(), nil)
		var cleanup *CleanupError
		if err == nil || errors.As(err, &cleanup) || !strings.Contains(err.Error(), "busy") {
			t.Fatalf("attempt %d: err = %v", i, err)
		}
	}
}

func TestExecutorPlatformFailureKeepsFacts(t *testing.T) {
	e, _ := fakeExecutor(t, printFacts(factsLine("platform", false, "init failed: phase=6 errno=2")))
	f, err := e.Execute(context.Background(), testJob(), nil)
	if err == nil || !strings.Contains(err.Error(), "init failed") || f.Reason != ReasonPlatform {
		t.Fatalf("facts=%+v err=%v", f, err)
	}
}

func TestExecutorCancellationClosesStdin(t *testing.T) {
	// 脚本读 stdin 直到 EOF：只有取消管道被关闭，它才会结束。
	e, _ := fakeExecutor(t, "cat > /dev/null\n"+printFacts(factsLine("cancelled", true, "")))
	ctx, cancel := context.WithCancel(context.Background())
	time.AfterFunc(100*time.Millisecond, cancel)
	f, err := e.Execute(ctx, testJob(), nil)
	if err != nil || f.Reason != ReasonCancelled {
		t.Fatalf("facts=%+v err=%v", f, err)
	}
}

func TestExecutorRejectsMalformedFacts(t *testing.T) {
	for name, line := range map[string]string{
		"unknown field":  `{"version":1,"extra":1}`,
		"trailing data":  factsLine("", false, "") + ` {}`,
		"bad version":    `{"version":2}`,
		"unknown reason": factsLine("exploded", false, ""),
		"unrequested artifact": strings.Replace(factsLine("", false, ""), `"outputs":[]`,
			`"outputs":[{"index":0,"path":"other","sizeBytes":1}]`, 1),
	} {
		t.Run(name, func(t *testing.T) {
			e, _ := fakeExecutor(t, printFacts(line))
			if _, err := e.Execute(context.Background(), testJob(), func(Facts, string, io.Reader) error { return nil }); err == nil {
				t.Fatal("malformed facts were accepted")
			}
		})
	}
}

func TestExecutorRejectsStreamLengthMismatch(t *testing.T) {
	// 事实说 stdout 为空，文件里却有 3 个字节。
	e, _ := fakeExecutor(t, printFacts(factsLine("", false, ""))+"printf abc > \"$d/out/stdout\"\n")
	if _, err := e.Execute(context.Background(), testJob(), nil); err == nil {
		t.Fatal("stdout that differs from the facts was delivered")
	}
}

func TestExecutorClearsTheBoxAfterEachRun(t *testing.T) {
	e, box := fakeExecutor(t, printFacts(factsLine("", false, "")))
	for i := 0; i < 2; i++ {
		if _, err := e.Execute(context.Background(), testJob(), nil); err != nil {
			t.Fatal(err)
		}
		// 交付之后 box 只剩空的 in/ 与 out/：源码、stdin、请求和输出都不留在磁盘上。
		entries, err := os.ReadDir(box)
		if err != nil || len(entries) != 2 {
			t.Fatalf("run %d: box = %v, %v", i, entries, err)
		}
		for _, sub := range []string{"in", "out"} {
			if left, err := os.ReadDir(filepath.Join(box, sub)); err != nil || len(left) != 0 {
				t.Fatalf("run %d: %s = %v, %v", i, sub, left, err)
			}
		}
	}
}

func TestExecutorBinaryMustBeSetuidRoot(t *testing.T) {
	bin := filepath.Join(t.TempDir(), "sandbox")
	if err := os.WriteFile(bin, []byte("#!/bin/sh\n"), 0o755); err != nil {
		t.Fatal(err)
	}
	if _, err := NewExecutor(bin, filepath.Join(t.TempDir(), "boxes"), 1); err == nil {
		t.Fatal("a non-setuid executor was accepted")
	}
	if _, err := NewExecutor("sandbox", filepath.Join(t.TempDir(), "boxes"), 1); err == nil {
		t.Fatal("a relative executor path was accepted")
	}
}
