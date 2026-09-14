package container

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
	"sync"
	"syscall"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
)

type hostContainer struct {
	workDir   string
	mu        sync.Mutex
	process   *hostProcess
	closed    bool
	closeOnce sync.Once
	closeErr  error
}

type hostProcess struct {
	cmd    *exec.Cmd
	ctx    context.Context
	cancel context.CancelFunc
	start  time.Time
	once   sync.Once
	usage  Usage
	err    error
}

// NewHost 仅用于 trusted-host 模式，不提供 cgroup、namespace 或 seccomp 隔离。
// Linux 隔离不可用时不能把它作为自动回退。
func NewHost() (*hostContainer, error) {
	dir, err := os.MkdirTemp("", "cherry-oj-*")
	if err != nil {
		return nil, err
	}
	return &hostContainer{
		workDir: dir,
	}, nil
}

func (p *hostProcess) Wait(ctx context.Context) (Usage, error) {
	stop := context.AfterFunc(ctx, p.cancel)
	defer stop()
	p.once.Do(func() { p.usage, p.err = p.wait() })
	return p.usage, p.err
}
func (p *hostProcess) wait() (Usage, error) {

	defer p.cancel()
	err := p.cmd.Wait()
	var ee *exec.ExitError

	// ExitError 是命令退出事实，不是等待失败；交给 runner 区分信号和非零退出。
	if err != nil && !errors.As(err, &ee) && !(p.cmd.ProcessState != nil && errors.Is(err, p.ctx.Err())) {
		return Usage{}, err
	}

	ps := p.cmd.ProcessState

	u := Usage{ExitCode: ps.ExitCode(), ClockNs: time.Since(p.start).Nanoseconds()}
	if p.ctx.Err() == context.DeadlineExceeded {
		u.Reason = hostexec.ReasonWall
	} else if p.ctx.Err() != nil {
		u.Reason = hostexec.ReasonCancelled
	}

	if ws, ok := ps.Sys().(syscall.WaitStatus); ok && ws.Signaled() {
		u.Signal = int(ws.Signal())
	}

	u.CPUNs = ps.UserTime().Nanoseconds() + ps.SystemTime().Nanoseconds()

	if ru, ok := ps.SysUsage().(*syscall.Rusage); ok {
		u.MemoryBytes = maxrssBytes(ru.Maxrss)
	}

	return u, nil
}

func (c *hostContainer) Start(ctx context.Context, s Spec) (Process, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed || c.process != nil {
		return nil, fmt.Errorf("Container只能执行一次")
	}

	if len(s.Command) == 0 {
		return nil, fmt.Errorf("container: empty command")
	}

	name := s.Command[0]

	if !strings.Contains(name, "/") {
		cand := filepath.Join(c.workDir, name)
		if file, err := os.Stat(cand); err == nil && !file.IsDir() {
			name = cand
		}
	}

	clock := s.Limits.ClockNs
	if clock <= 0 {
		clock = int64(5 * time.Second)
	}
	runCtx, cancel := context.WithTimeout(ctx, time.Duration(clock))
	cmd := exec.CommandContext(runCtx, name, s.Command[1:]...)

	cmd.Dir = c.workDir
	cmd.Env = s.Env
	cmd.Stdin = s.Stdin
	cmd.Stdout = s.Stdout
	cmd.Stderr = s.Stderr

	// 给本次命令独立进程组，使取消能覆盖仍留在该组的编译器等后代。
	cmd.SysProcAttr = &syscall.SysProcAttr{
		Setpgid: true,
	}

	// 杀单个主进程会留下仍占用输出管道的后代，因此对整个进程组发信号。
	cmd.Cancel = func() error {
		pid := cmd.Process.Pid
		if pid <= 0 {
			return os.ErrProcessDone
		}
		err := syscall.Kill(-pid, syscall.SIGKILL)
		if err != nil {
			// 进程退出与输出溢出取消可同时发生。确认主进程已消失才把迟到的取消视为完成。
			_, groupErr := syscall.Getpgid(pid)
			if errors.Is(groupErr, syscall.ESRCH) {
				return os.ErrProcessDone
			}
			probe := cmd.Process.Signal(syscall.Signal(0))
			if errors.Is(probe, os.ErrProcessDone) || errors.Is(probe, syscall.ESRCH) {
				return os.ErrProcessDone
			}
		}
		return err
	}

	// 后代可能持有 stdout/stderr 管道；主进程退出后仍要限制复制 goroutine 的等待。
	cmd.WaitDelay = 2 * time.Second

	start := time.Now()
	if err := cmd.Start(); err != nil {
		cancel()
		return nil, err
	}

	c.process = &hostProcess{cmd: cmd, ctx: runCtx, cancel: cancel, start: start}
	return c.process, nil
}

func (c *hostContainer) PutFile(name string, r io.Reader, mode os.FileMode) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed || c.process != nil {
		return fmt.Errorf("工作区不再接受输入")
	}
	full, err := c.resolve(name)
	if err != nil {
		return err
	}

	if err := os.MkdirAll(filepath.Dir(full), 0o755); err != nil {
		return err
	}

	f, err := os.OpenFile(full, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
	if err != nil {
		return err
	}

	if _, err := io.Copy(f, r); err != nil {
		f.Close()
		return err
	}

	return f.Close()
}

func (c *hostContainer) GetFile(name string) (io.ReadCloser, error) {
	full, err := c.resolve(name)
	if err != nil {
		return nil, err
	}
	return os.Open(full)
}

func (c *hostContainer) Close() error {
	c.closeOnce.Do(func() {
		c.mu.Lock()
		c.closed = true
		p := c.process
		c.mu.Unlock()
		if p != nil {
			p.cancel()
			_, err := p.Wait(context.Background())
			c.closeErr = err
		}
		c.closeErr = errors.Join(c.closeErr, os.RemoveAll(c.workDir))
	})
	return c.closeErr
}

func (c *hostContainer) resolve(name string) (string, error) {
	// 这里只限制字符串路径；host 中的用户程序仍可创建链接，不能视为隔离安全边界。
	clean := filepath.Clean("/" + name)
	full := filepath.Join(c.workDir, clean)

	rel, err := filepath.Rel(c.workDir, full)

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
