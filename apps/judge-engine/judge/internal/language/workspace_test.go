package language_test

import (
	"context"
	"errors"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"syscall"
	"testing"
	"time"
)

// 这个夹具在测试内自备一个「临时目录 + 起进程」的最小工作间，不引用 sandbox 的实现。
//
// 原先它直接用 sandbox 的零隔离后端。按信任边界重切后 judge 子树不能引用
// sandbox/internal，编译器会直接拒绝——而这正是要守住的那条线：judge 只能通过 HTTP
// 使用 sandbox，不能进程内调用它的执行实现。
//
// 本文件只服务于「语言配置真的能编译、能运行、产物收全了没有」这一个断言，
// 不提供任何隔离或限额，也不试图复刻 sandbox 的后端语义。
type workspace struct{ dir string }

// usage 的字段名与顺序是诊断脚本的输入格式（deploy/sandbox-linux/ci/diagnose_language.py
// 按 %+v 的文本解析 java 编译耗时），改动字段即改动该脚本的契约。
type usage struct {
	ExitCode        int
	Signal          int
	CPUNs           int64
	MemoryBytes     int64
	ClockNs         int64
	Reason          string
	OOMKilled       bool
	GroupAccounting bool
}

func newWorkspace(t *testing.T) *workspace {
	t.Helper()
	return &workspace{dir: t.TempDir()}
}

func (w *workspace) put(t *testing.T, name string, r io.Reader, mode os.FileMode) {
	t.Helper()
	f, err := os.OpenFile(filepath.Join(w.dir, name), os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := io.Copy(f, r); err != nil {
		f.Close()
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}
}

func (w *workspace) get(t *testing.T, name string) []byte {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(w.dir, name))
	if err != nil {
		t.Fatalf("收产物 %s: %v", name, err)
	}
	return data
}

// run 复刻语言配置依赖的两条解析规则：命令在工作目录中解析（`Main` 即 ./Main），
// 且进程的工作目录就是工作间。墙钟为 0 表示不设上限。
func (w *workspace) run(command []string, stdin io.Reader, stdout, stderr io.Writer, clock time.Duration) (usage, error) {
	name := command[0]
	if !strings.Contains(name, "/") {
		candidate := filepath.Join(w.dir, name)
		if info, err := os.Stat(candidate); err == nil && !info.IsDir() {
			name = candidate
		}
	}
	ctx := context.Background()
	cancel := context.CancelFunc(func() {})
	if clock > 0 {
		ctx, cancel = context.WithTimeout(ctx, clock)
	}
	defer cancel()

	cmd := exec.CommandContext(ctx, name, command[1:]...)
	cmd.Dir, cmd.Stdin, cmd.Stdout, cmd.Stderr = w.dir, stdin, stdout, stderr
	// 编译器会留下后代；给独立进程组，取消时整组收掉。
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	cmd.Cancel = func() error { return syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL) }
	cmd.WaitDelay = 2 * time.Second

	start := time.Now()
	err := cmd.Run()
	var exitErr *exec.ExitError
	if err != nil && !errors.As(err, &exitErr) {
		return usage{ExitCode: -1}, err
	}
	state := cmd.ProcessState
	u := usage{
		ExitCode: state.ExitCode(),
		CPUNs:    state.UserTime().Nanoseconds() + state.SystemTime().Nanoseconds(),
		ClockNs:  time.Since(start).Nanoseconds(),
	}
	if ws, ok := state.Sys().(syscall.WaitStatus); ok && ws.Signaled() {
		u.Signal = int(ws.Signal())
	}
	if ru, ok := state.SysUsage().(*syscall.Rusage); ok {
		u.MemoryBytes = ru.Maxrss
		if runtime.GOOS == "linux" { // Linux 的 Maxrss 是 KiB，Darwin 是 bytes
			u.MemoryBytes *= 1024
		}
	}
	if ctx.Err() != nil {
		u.Reason = "wall"
	}
	return u, nil
}
