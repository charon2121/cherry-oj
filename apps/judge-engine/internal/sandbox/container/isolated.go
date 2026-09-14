package container

import (
	"context"
	"errors"
	"fmt"
	"io"
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

// done 的关闭发布 usage/err 以及 Container.outputs；Wait/GetFile/Close 先等待它，
// 避免读半成品或在 execute 写文件时删除暂存目录。
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

func (c *isolatedContainer) Start(ctx context.Context, s Spec) (Process, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed || c.attempted {
		return nil, fmt.Errorf("Container只能执行一次")
	}
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	// 即使后面的准备失败，也不能重用已有部分输入状态；由 Close 统一回收。
	c.attempted = true
	r, err := c.prepareRequest(s)
	if err != nil {
		return nil, err
	}
	// inputs 的声明和 files 使用相同顺序，stdin 最后追加；线上没有逐文件分隔符。
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

// execute 在临时文件中接收产物，只有 Call 确认完整交付及连接收尾后才允许 GetFile。
func (c *isolatedContainer) execute(ctx context.Context, p *isolatedProcess, s Spec, r launcher.Request, input io.ReadCloser) {
	defer close(p.done)
	defer p.cancel()
	result, err := helper.Call(ctx, c.socket, r, input, c.receiveOutput)
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
	// 协议帧正确不等于执行组已经清空；残留后代时不能交付成功或复用容量。
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
		// 取消只发出终止请求；仍要等 Call 解除 I/O 并交还所有输入资源。
		p.cancel()
		<-p.done
	}
	return p.usage, p.err
}

func (c *isolatedContainer) Close() error {
	c.closeOnce.Do(func() {
		c.mu.Lock()
		c.closed = true
		p := c.process
		c.mu.Unlock()
		// execute 可能还在落盘；必须等 done 后才能 RemoveAll。
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
