// Package pool 管理执行容量。每次执行独占一个 Container，不复用工作区或资源计量。
package pool

import (
	"context"
	"errors"
	"fmt"
	"sync"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/container"
	"cherry-oj/judge-engine/internal/sandbox/runner"
	"cherry-oj/judge-engine/internal/sandbox/store"
)

type admissionError string

func (e admissionError) Error() string     { return string(e) }
func (e admissionError) Unavailable() bool { return true }

var ErrBusy = admissionError("sandbox queue is full")
var ErrClosed = admissionError("sandbox pool is closed")

type Options struct {
	Parallelism int
	QueueSize   int
	Factory     func() (container.Container, error)
}

type Pool struct {
	sem       chan struct{}
	admitted  chan struct{}
	store     store.Store
	factory   func() (container.Container, error)
	ctx       context.Context
	cancel    context.CancelFunc
	mu        sync.Mutex
	closed    bool
	wg        sync.WaitGroup
	closeOnce sync.Once
	closeErr  error
}

func New(st store.Store, opts Options) (*Pool, error) {
	if st == nil || opts.Factory == nil || opts.Parallelism <= 0 || opts.Parallelism > 256 || opts.QueueSize <= 0 || opts.QueueSize > 1024 {
		return nil, fmt.Errorf("pool需要store、工厂及有界正数parallelism/queueSize")
	}
	ctx, cancel := context.WithCancel(context.Background())
	return &Pool{sem: make(chan struct{}, opts.Parallelism), admitted: make(chan struct{}, opts.Parallelism+opts.QueueSize), store: st, factory: opts.Factory, ctx: ctx, cancel: cancel}, nil
}

func (p *Pool) Run(ctx context.Context, spec contract.RunSpec) (res contract.RunResult, err error) {
	p.mu.Lock()
	if p.closed {
		p.mu.Unlock()
		return res, ErrClosed
	}
	select {
	case p.admitted <- struct{}{}:
	default:
		p.mu.Unlock()
		return res, ErrBusy
	}
	p.wg.Add(1)
	p.mu.Unlock()
	defer p.wg.Done()
	defer func() { <-p.admitted }()
	runCtx, cancel := context.WithCancel(ctx)
	stop := context.AfterFunc(p.ctx, cancel)
	defer stop()
	defer cancel()
	select {
	case <-runCtx.Done():
		return res, runCtx.Err()
	case p.sem <- struct{}{}:
	}
	defer func() { <-p.sem }()
	if err := runCtx.Err(); err != nil {
		return res, err
	}
	// AfterFunc取消回调异步执行，不能只检查派生ctx；池关闭必须同步阻止排队任务启动。
	if p.ctx.Err() != nil {
		return res, ErrClosed
	}
	c, err := p.factory()
	if err != nil {
		return res, fmt.Errorf("创建容器: %w", err)
	}
	if c == nil {
		return res, fmt.Errorf("工厂返回空Container")
	}
	// 生命周期兜底也覆盖调用者/实现意外panic；不把清理责任留给HTTP恢复器。
	defer func() {
		// Close完成之前不发布任何成功响应；失败停接单，不能把容量归还后继续运行。
		if closeErr := c.Close(); closeErr != nil {
			p.mu.Lock()
			p.closed = true
			p.closeErr = errors.Join(p.closeErr, closeErr)
			p.cancel()
			p.mu.Unlock()
			var rollback error
			for _, ref := range res.Artifacts {
				rollback = errors.Join(rollback, p.store.Delete(ref))
			}
			res.Artifacts = nil
			res.Outputs = nil
			res.Status = contract.StatusInternalError
			res.Error = errors.Join(errors.New("容器清理失败"), closeErr, rollback).Error()
		}
	}()
	res = runner.Run(runCtx, c, p.store, spec)
	return res, nil
}

// Close 拒绝新请求，取消排队及在途执行，并等待各自的独立资源回收。
func (p *Pool) Close() error {
	p.closeOnce.Do(func() {
		p.mu.Lock()
		p.closed = true
		p.cancel()
		p.mu.Unlock()
		p.wg.Wait()
	})
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.closeErr
}
