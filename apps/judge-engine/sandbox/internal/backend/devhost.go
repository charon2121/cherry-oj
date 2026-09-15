package backend

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"syscall"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
)

// Name 用于配置中选择后端；取值同时出现在配置校验与装配处，只定义一次。
const (
	NameLinux   = "linux"
	NameDevHost = "devhost"
)

// DevHost 是**零隔离**的开发后端：隔离那条轴上什么都不做，只给工作目录这一层最弱边界。
//
// 它存在的理由只有一个——让非 Linux 平台能开发和调试整条链路。跑在它里面的程序可以读宿主
// 任意文件、可以联网、可以 fork 炸弹。
//
// 特别注意 Limits：本后端**不强制**任何 CPU、内存或进程数限额，只有墙钟会真正生效（用于杀死
// 卡住的进程），其余数值仅作为事实回报给调用方事后比较。旧名 trusted-host 听起来像一种可选的
// 信任模式，实际是「没有隔离」，因此改名 devhost，并要求配置显式承认才能启用。
type DevHost struct{}

func NewDevHost() *DevHost { return &DevHost{} }

func (b *DevHost) Execute(ctx context.Context, j Job, sink OutputSink) (facts Facts, err error) {
	if len(j.Command) == 0 {
		return Facts{}, fmt.Errorf("command must not be empty")
	}
	dir, err := os.MkdirTemp("", "cherry-oj-*")
	if err != nil {
		return Facts{}, err
	}
	// 工作目录回收失败意味着残留文件会与后续执行重叠，属于回收未确认。
	defer func() {
		if e := os.RemoveAll(dir); e != nil {
			err = errors.Join(err, cleanupFailed("%w", e))
		}
	}()

	for _, in := range j.Inputs {
		if err := writeInto(dir, in); err != nil {
			return Facts{}, err
		}
	}

	facts, err = runCommand(ctx, dir, j)
	if err != nil {
		return facts, err
	}
	if sink == nil {
		return facts, nil
	}
	for _, name := range j.Outputs {
		full, err := resolve(dir, name)
		if err != nil {
			return facts, err
		}
		f, err := os.Open(full)
		if os.IsNotExist(err) {
			continue // 未产出该文件；是否算失败由调用方按 Outputs 判断
		}
		if err != nil {
			return facts, err
		}
		err = sink(facts, name, f)
		if closeErr := f.Close(); err == nil {
			err = closeErr
		}
		if err != nil {
			return facts, err
		}
	}
	return facts, nil
}

func runCommand(ctx context.Context, dir string, j Job) (Facts, error) {
	// 命令先在工作目录中解析（`Main` 即 ./Main），解析不到再交给 PATH。
	name := j.Command[0]
	if !strings.Contains(name, "/") {
		if info, err := os.Stat(filepath.Join(dir, name)); err == nil && !info.IsDir() {
			name = filepath.Join(dir, name)
		}
	}
	clock := j.Limits.ClockNs
	if clock <= 0 {
		return Facts{}, fmt.Errorf("the wall-clock limit must be positive, got %d", clock)
	}
	runCtx, cancel := context.WithTimeout(ctx, time.Duration(clock))
	defer cancel()

	cmd := exec.CommandContext(runCtx, name, j.Command[1:]...)
	cmd.Dir, cmd.Env = dir, j.Env
	if j.Stdin != nil {
		cmd.Stdin = j.Stdin.Reader
	}
	cmd.Stdout, cmd.Stderr = j.Stdout, j.Stderr
	// 给本次命令独立进程组，使取消能覆盖仍留在该组的编译器等后代。
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	// 杀单个主进程会留下仍占用输出管道的后代，因此对整个进程组发信号。
	cmd.Cancel = func() error { return killGroup(cmd) }
	// 后代可能持有 stdout/stderr 管道；主进程退出后仍要限制复制 goroutine 的等待。
	cmd.WaitDelay = 2 * time.Second

	start := time.Now()
	err := cmd.Run()
	var exitErr *exec.ExitError
	// ExitError 是命令退出事实，不是等待失败；交给调用方区分信号和非零退出。
	if err != nil && !errors.As(err, &exitErr) && !(cmd.ProcessState != nil && errors.Is(err, runCtx.Err())) {
		return Facts{}, err
	}
	state := cmd.ProcessState
	facts := Facts{ExitCode: state.ExitCode(), ClockNs: time.Since(start).Nanoseconds(),
		CPUNs: state.UserTime().Nanoseconds() + state.SystemTime().Nanoseconds()}
	switch {
	case errors.Is(runCtx.Err(), context.DeadlineExceeded) && ctx.Err() == nil:
		facts.Reason = hostexec.ReasonWall
	case runCtx.Err() != nil:
		facts.Reason = hostexec.ReasonCancelled
	}
	if ws, ok := state.Sys().(syscall.WaitStatus); ok && ws.Signaled() {
		facts.Signal = int(ws.Signal())
	}
	if ru, ok := state.SysUsage().(*syscall.Rusage); ok {
		facts.MemoryBytes = maxrssBytes(ru.Maxrss)
	}
	return facts, nil
}

func killGroup(cmd *exec.Cmd) error {
	pid := cmd.Process.Pid
	if pid <= 0 {
		return os.ErrProcessDone
	}
	err := syscall.Kill(-pid, syscall.SIGKILL)
	if err != nil {
		// 进程退出与输出溢出取消可同时发生。确认主进程已消失才把迟到的取消视为完成。
		if _, groupErr := syscall.Getpgid(pid); errors.Is(groupErr, syscall.ESRCH) {
			return os.ErrProcessDone
		}
		probe := cmd.Process.Signal(syscall.Signal(0))
		if errors.Is(probe, os.ErrProcessDone) || errors.Is(probe, syscall.ESRCH) {
			return os.ErrProcessDone
		}
	}
	return err
}

func writeInto(dir string, in NamedSource) error {
	full, err := resolve(dir, in.Name)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(full), 0o755); err != nil {
		return err
	}
	mode := os.FileMode(0o644)
	if in.Executable {
		mode = 0o755
	}
	f, err := os.OpenFile(full, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
	if err != nil {
		return err
	}
	if _, err := io.Copy(f, in.Reader); err != nil {
		f.Close()
		return err
	}
	return f.Close()
}

// resolve 只限制字符串路径；本后端中的用户程序仍可创建链接，不能视为隔离安全边界。
func resolve(dir, name string) (string, error) {
	full := filepath.Join(dir, filepath.Clean("/"+name))
	rel, err := filepath.Rel(dir, full)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return "", fmt.Errorf("path escapes workdir: %q", name)
	}
	return full, nil
}

// getrusage 的 Maxrss 在 Linux 是 KiB、Darwin 是 bytes；契约统一为 bytes。
func maxrssBytes(maxrss int64) int64 {
	if runtime.GOOS == "linux" {
		return maxrss * 1024
	}
	return maxrss
}
