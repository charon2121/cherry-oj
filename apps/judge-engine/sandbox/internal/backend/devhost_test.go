package backend

import (
	"bytes"
	"context"
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
)

var _ Backend = (*DevHost)(nil)

func limits(clock time.Duration) contract.Limits {
	return contract.ExplicitLimits(contract.Limits{CPUNs: int64(time.Second), ClockNs: int64(clock),
		MemoryBytes: 256 << 20, MaxProcesses: 32, StdoutMaxBytes: 1 << 20, StderrMaxBytes: 1 << 20})
}

// collect 把交付的产物收进 map，顺便记录交付时后端给出的事实。
func collect(into map[string]string, seen *Facts) OutputSink {
	return func(f Facts, name string, r io.Reader) error {
		data, err := io.ReadAll(r)
		if err != nil {
			return err
		}
		into[name] = string(data)
		*seen = f
		return nil
	}
}

func TestDevHostInputRoundTripsThroughWorkdir(t *testing.T) {
	out := map[string]string{}
	var seen Facts
	facts, err := NewDevHost().Execute(context.Background(), Job{
		Command: []string{"sh", "-c", "cat main.cpp > copy.txt"},
		Inputs:  []NamedSource{{Name: "main.cpp", Source: Source{Reader: strings.NewReader("int main(){}")}}},
		Outputs: []string{"copy.txt"},
		Limits:  limits(5 * time.Second),
	}, collect(out, &seen))
	if err != nil {
		t.Fatal(err)
	}
	if facts.ExitCode != 0 {
		t.Fatalf("exit=%d", facts.ExitCode)
	}
	if out["copy.txt"] != "int main(){}" {
		t.Fatalf("产物内容=%q", out["copy.txt"])
	}
	if seen.ExitCode != facts.ExitCode {
		t.Fatal("交付时给出的事实与返回的事实不一致")
	}
}

// Execute 返回即代表工作区已经回收：不存在需要调用方再关闭的对象。
func TestDevHostRemovesWorkdirBeforeReturning(t *testing.T) {
	out := map[string]string{}
	var seen Facts
	_, err := NewDevHost().Execute(context.Background(), Job{
		Command: []string{"sh", "-c", "pwd > where.txt"},
		Outputs: []string{"where.txt"},
		Limits:  limits(5 * time.Second),
	}, collect(out, &seen))
	if err != nil {
		t.Fatal(err)
	}
	dir := strings.TrimSpace(out["where.txt"])
	if dir == "" {
		t.Fatal("没有拿到工作目录")
	}
	if _, err := os.Stat(dir); !os.IsNotExist(err) {
		t.Fatalf("工作目录在返回后仍然存在: %v", err)
	}
}

// 墙钟到点要杀掉整个进程组，并把原因报成 wall——不是取消，也不是平台故障。
func TestDevHostWallClockKillsProcessGroup(t *testing.T) {
	start := time.Now()
	facts, err := NewDevHost().Execute(context.Background(), Job{
		Command: []string{"sh", "-c", "sleep 30 & sleep 30"},
		Limits:  limits(300 * time.Millisecond),
	}, nil)
	if err != nil {
		t.Fatal(err)
	}
	if elapsed := time.Since(start); elapsed > 5*time.Second {
		t.Fatalf("墙钟没有生效，耗时 %s", elapsed)
	}
	if facts.Reason != hostexec.ReasonWall {
		t.Fatalf("reason=%q want wall", facts.Reason)
	}
}

// 请求取消与墙钟超时必须区分开：取消是调用方的决定，不是这次执行超了预算。
func TestDevHostCancellationIsNotWall(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	go func() { time.Sleep(200 * time.Millisecond); cancel() }()
	facts, err := NewDevHost().Execute(ctx, Job{
		Command: []string{"sleep", "30"},
		Limits:  limits(30 * time.Second),
	}, nil)
	if err != nil {
		t.Fatal(err)
	}
	if facts.Reason != hostexec.ReasonCancelled {
		t.Fatalf("reason=%q want cancelled", facts.Reason)
	}
}

// 路径要么被拒绝，要么被钳进工作区内——两者都安全，逃出去才不安全。
// 注意这只挡住字符串路径：本后端中的用户程序仍可创建链接，不构成隔离边界。
func TestResolveNeverLeavesWorkdir(t *testing.T) {
	dir := t.TempDir()
	for _, name := range []string{"../escape", "../../etc/passwd", "/etc/passwd", "a/../../b", "sub/../../../x"} {
		got, err := resolve(dir, name)
		if err != nil {
			continue // 直接拒绝也算安全
		}
		rel, relErr := filepath.Rel(dir, got)
		if relErr != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
			t.Errorf("resolve(%q)=%q 逃出了工作区", name, got)
		}
	}
}

// 越界的输入名不能在宿主上落下文件。
func TestDevHostInputCannotWriteOutsideWorkdir(t *testing.T) {
	probe := filepath.Join(t.TempDir(), "escape-probe")
	_, err := NewDevHost().Execute(context.Background(), Job{
		Command: []string{"true"},
		Inputs:  []NamedSource{{Name: "../../../../../../.." + probe, Source: Source{Reader: strings.NewReader("x")}}},
		Limits:  limits(5 * time.Second),
	}, nil)
	if err != nil {
		t.Logf("越界输入被拒绝: %v", err)
	}
	if _, statErr := os.Stat(probe); !os.IsNotExist(statErr) {
		t.Fatalf("越界输入在宿主上落下了文件: %v", statErr)
	}
}

func TestDevHostRejectsEmptyCommandAndClock(t *testing.T) {
	if _, err := NewDevHost().Execute(context.Background(), Job{Limits: limits(time.Second)}, nil); err == nil {
		t.Fatal("空命令被接受")
	}
	_, err := NewDevHost().Execute(context.Background(), Job{
		Command: []string{"true"},
		Limits:  contract.ExplicitLimits(contract.Limits{CPUNs: 1, ClockNs: 0, MemoryBytes: 1}),
	}, nil)
	if err == nil {
		t.Fatal("零墙钟被接受")
	}
}

// 非零退出是执行事实，不是 Execute 的错误——混了会把用户程序的失败报成平台故障。
func TestDevHostNonzeroExitIsNotAnError(t *testing.T) {
	facts, err := NewDevHost().Execute(context.Background(), Job{
		Command: []string{"sh", "-c", "exit 3"},
		Limits:  limits(5 * time.Second),
	}, nil)
	if err != nil {
		t.Fatalf("非零退出不应当作为错误返回: %v", err)
	}
	if facts.ExitCode != 3 {
		t.Fatalf("exit=%d want 3", facts.ExitCode)
	}
}

// 输入声明为可执行时必须真的能跑起来：判题把编译产物作为输入送回执行那一步。
func TestDevHostRunsExecutableInput(t *testing.T) {
	var out bytes.Buffer
	facts, err := NewDevHost().Execute(context.Background(), Job{
		Command: []string{"prog"},
		Inputs: []NamedSource{{Name: "prog", Source: Source{
			Reader: strings.NewReader("#!/bin/sh\necho ran\n"), Executable: true}}},
		Limits: limits(5 * time.Second),
		Stdout: &out,
	}, nil)
	if err != nil {
		t.Fatal(err)
	}
	if facts.ExitCode != 0 || strings.TrimSpace(out.String()) != "ran" {
		t.Fatalf("exit=%d stdout=%q", facts.ExitCode, out.String())
	}
}

func TestDevHostFeedsStdinAndRecordsUsage(t *testing.T) {
	var out bytes.Buffer
	facts, err := NewDevHost().Execute(context.Background(), Job{
		Command: []string{"sh", "-c", "cat; i=0; while [ $i -lt 20000 ]; do i=$((i+1)); done"},
		Stdin:   &Source{Reader: strings.NewReader("fed\n")},
		Limits:  limits(10 * time.Second),
		Stdout:  &out,
	}, nil)
	if err != nil {
		t.Fatal(err)
	}
	if strings.TrimSpace(out.String()) != "fed" {
		t.Fatalf("stdout=%q", out.String())
	}
	if facts.CPUNs <= 0 || facts.ClockNs <= 0 || facts.MemoryBytes <= 0 {
		t.Fatalf("资源事实缺失: %+v", facts)
	}
	// devhost 没有整组计量能力，调用方据此改用峰值推断内存。
	if facts.GroupAccounting {
		t.Fatal("零隔离后端不应声称整组计量")
	}
}

// 回收失败必须表达成 CleanupError：容量不能归还给下一条命令。
func TestCleanupErrorIsDistinguishable(t *testing.T) {
	err := cleanupFailed("目录残留")
	var target *CleanupError
	if !errors.As(err, &target) {
		t.Fatal("回收失败没有被识别为 CleanupError")
	}
	if !strings.Contains(err.Error(), "回收未确认") {
		t.Fatalf("错误信息没有点明回收未确认: %v", err)
	}
}
