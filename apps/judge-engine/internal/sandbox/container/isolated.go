package container

import (
	"context"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"sync"

	"cherry-oj/judge-engine/internal/sandbox/helper"
	"cherry-oj/judge-engine/internal/sandbox/launcher"
)

// isolatedContainer 暂存的是服务自己创建的普通文件，用户代码从不接触此目录。
// 逻辑路径只作 map key；随机物理文件名不会拼入用户路径。每个实例只启动一次。
// PutFile/Start/GetFile 由一个调用者依序使用；Close 可与执行并发。
type isolatedContainer struct {
	socket, dir string
	inputs      []launcher.Input
	files       []*os.File
	outputs     map[string]string
	bytes       int64
	mu          sync.Mutex
	process     *isolatedProcess
	closed      bool
	attempted   bool
	closeOnce   sync.Once
	closeErr    error
}

type isolatedProcess struct {
	cancel context.CancelFunc
	done   chan struct{}
	usage  Usage
	err    error
}

// NewIsolated 创建客户端工作区，不会自动选择 host 后端。
// root 必须由服务独占、预先创建，且位于项目配置指定位置。
func NewIsolated(socket, root string) (*isolatedContainer, error) {
	if socket == "" || root == "" {
		return nil, fmt.Errorf("helper socket/暂存目录不能为空")
	}
	info, err := os.Lstat(root)
	if err != nil {
		return nil, err
	}
	if !info.IsDir() || !owned(info) || info.Mode().Perm()&0o077 != 0 {
		return nil, fmt.Errorf("暂存目录必须为私有目录: %s", root)
	}
	dir, err := os.MkdirTemp(root, "execution-")
	if err != nil {
		return nil, err
	}
	return &isolatedContainer{socket: socket, dir: dir, outputs: map[string]string{}}, nil
}

func (c *isolatedContainer) PutFile(name string, r io.Reader, mode fs.FileMode) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed || c.attempted {
		return fmt.Errorf("工作区不再接受输入")
	}
	if !launcher.ValidPath(name) || len(c.inputs) >= 128 {
		return fmt.Errorf("输入路径/数量无效: %q", name)
	}
	for _, f := range c.inputs {
		if f.Path == name {
			return fmt.Errorf("重复输入: %q", name)
		}
	}
	f, n, err := c.spool(r, launcher.MaxInputBytes-c.bytes)
	if err != nil {
		return err
	}
	c.files = append(c.files, f)
	c.inputs = append(c.inputs, launcher.Input{Path: name, SizeBytes: n, Executable: mode&0o111 != 0})
	c.bytes += n
	return nil
}

func (c *isolatedContainer) spool(r io.Reader, limit int64) (*os.File, int64, error) {
	if r == nil {
		return nil, 0, fmt.Errorf("输入流为空")
	}
	f, err := os.CreateTemp(c.dir, "data-")
	if err != nil {
		return nil, 0, err
	}
	n, err := io.Copy(f, io.LimitReader(r, limit+1))
	if err == nil && n > limit {
		err = fmt.Errorf("文件总量超过 %d bytes", limit)
	}
	if err == nil {
		_, err = f.Seek(0, io.SeekStart)
	}
	if err != nil {
		return nil, 0, errors.Join(err, f.Close(), os.Remove(f.Name()))
	}
	return f, n, nil
}

func (c *isolatedContainer) Start(ctx context.Context, s Spec) (Process, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed || c.attempted {
		return nil, fmt.Errorf("Container只能执行一次")
	}
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	c.attempted = true
	r := launcher.Request{Version: launcher.Version, Command: s.Command, Env: s.Env, Inputs: c.inputs, Outputs: s.Outputs, Limits: s.Limits}
	if s.Stdin != nil {
		f, n, err := c.spool(s.Stdin, launcher.MaxInputBytes-c.bytes)
		if err != nil {
			return nil, err
		}
		c.files = append(c.files, f)
		r.StdinBytes = n
	}
	if err := r.Validate(); err != nil {
		return nil, err
	}
	readers := make([]io.Reader, len(c.files))
	for i, f := range c.files {
		readers[i] = f
	}
	runCtx, cancel := context.WithCancel(ctx)
	p := &isolatedProcess{cancel: cancel, done: make(chan struct{})}
	c.process = p
	// input.Close 由 helper.Call 接管；Container 仅在尚未启动时关闭输入FD。
	input := &inputStream{Reader: io.MultiReader(readers...), files: c.files}
	c.files = nil
	go c.execute(runCtx, p, s, r, input)
	return p, nil
}

func (c *isolatedContainer) execute(ctx context.Context, p *isolatedProcess, s Spec, r launcher.Request, input io.ReadCloser) {
	defer close(p.done)
	defer p.cancel()
	result, err := helper.Call(ctx, c.socket, r, input, func(o helper.Output, reader io.Reader) error {
		f, n, e := c.spool(reader, o.SizeBytes)
		if e != nil {
			return e
		}
		closeErr := f.Close()
		if n != o.SizeBytes {
			return errors.Join(io.ErrUnexpectedEOF, closeErr)
		}
		if closeErr != nil {
			return closeErr
		}
		c.outputs[o.Path] = f.Name()
		return nil
	})
	p.usage = Usage{ExitCode: result.ExitCode, Signal: result.Signal, CPUNs: result.Usage.CPUNs, MemoryBytes: result.Usage.MemoryBytes, ClockNs: result.ClockNs, Reason: Reason(result.Reason), GroupAccounting: true, OOMKilled: result.Usage.OOM > 0 && result.Usage.OOMKill > 0}
	if result.Cancelled {
		p.usage.Reason = ReasonCancelled
	}
	if result.OutputExceeded && p.usage.Reason == "" {
		p.usage.Reason = ReasonOutput
	}
	if result.Error != "" {
		err = errors.Join(err, errors.New(result.Error))
	}
	if result.Usage.CPUNs < 0 || result.Usage.MemoryBytes < 0 || result.ClockNs < 0 {
		err = errors.Join(err, fmt.Errorf("helper返回负资源事实"))
	}
	if result.Usage.Populated {
		err = errors.Join(err, fmt.Errorf("helper返回未清空的执行组"))
	}
	if int64(len(result.Stdout)) > s.Limits.StdoutMaxBytes || int64(len(result.Stderr)) > s.Limits.StderrMaxBytes {
		err = errors.Join(err, fmt.Errorf("helper输出超过请求上限"))
	}
	if err == nil {
		if s.Stdout != nil {
			_, e := s.Stdout.Write(result.Stdout)
			err = errors.Join(err, e)
		}
		if s.Stderr != nil {
			_, e := s.Stderr.Write(result.Stderr)
			err = errors.Join(err, e)
		}
	}
	p.err = err
}

func (p *isolatedProcess) Wait(ctx context.Context) (Usage, error) {
	select {
	case <-p.done:
	case <-ctx.Done():
		p.cancel()
		<-p.done
	}
	return p.usage, p.err
}

func (c *isolatedContainer) GetFile(name string) (io.ReadCloser, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed || c.process == nil {
		return nil, fmt.Errorf("产物尚不可读")
	}
	select {
	case <-c.process.done:
	default:
		return nil, fmt.Errorf("执行尚未完成")
	}
	if c.process.err != nil {
		return nil, fmt.Errorf("回收/传输未确认: %w", c.process.err)
	}
	path, ok := c.outputs[name]
	if !ok {
		return nil, fmt.Errorf("未交付产物: %q", name)
	}
	return os.Open(path)
}

func (c *isolatedContainer) Close() error {
	c.closeOnce.Do(func() {
		c.mu.Lock()
		c.closed = true
		p := c.process
		c.mu.Unlock()
		if p != nil {
			p.cancel()
			<-p.done
		}
		for _, f := range c.files {
			c.closeErr = errors.Join(c.closeErr, f.Close())
		}
		c.closeErr = errors.Join(c.closeErr, os.RemoveAll(c.dir))
	})
	return c.closeErr
}

type inputStream struct {
	io.Reader
	files []*os.File
	once  sync.Once
	err   error
}

func (s *inputStream) Close() error {
	s.once.Do(func() {
		for _, f := range s.files {
			s.err = errors.Join(s.err, f.Close())
		}
	})
	return s.err
}
