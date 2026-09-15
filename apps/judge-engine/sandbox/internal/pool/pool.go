// Package pool 管理执行容量。每次执行独占一个 Container，不复用工作区或资源计量。
package pool

import (
	"context"
	"errors"
	"fmt"
	"sync"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/internal/container"
	"cherry-oj/judge-engine/sandbox/internal/runner"
	"cherry-oj/judge-engine/sandbox/internal/store"
)

type admissionError string

func (e admissionError) Error() string     { return string(e) }
func (e admissionError) Unavailable() bool { return true }

var ErrBusy = admissionError("sandbox queue is full")
var ErrClosed = admissionError("sandbox pool is closed")

const (
	maxParallelism = 256
	maxQueueSize   = 1024
)

// Options 的 Factory 为每次获准执行创建新 Container；共享 Store 由调用者关闭。
type Options struct {
	Parallelism int
	QueueSize   int
	Factory     func() (container.Container, error)
}

// Pool 分别限制已接纳请求与实际执行，避免排队请求无限占用服务资源。
type Pool struct {
	sem       chan struct{} // 持有到 Container.Close 完成，防止清理中的执行与新任务重叠。
	admitted  chan struct{} // 同时计入执行中和排队中的请求。
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

// New 不预创建容器；只有取得执行名额后才调用 Factory，排队不占工作区。
func New(st store.Store, opts Options) (*Pool, error) {
	if st == nil || opts.Factory == nil || opts.Parallelism <= 0 || opts.Parallelism > maxParallelism || opts.QueueSize <= 0 || opts.QueueSize > maxQueueSize {
		return nil, fmt.Errorf("pool需要store、工厂及有界正数parallelism/queueSize")
	}
	ctx, cancel := context.WithCancel(context.Background())
	return &Pool{sem: make(chan struct{}, opts.Parallelism), admitted: make(chan struct{}, opts.Parallelism+opts.QueueSize), store: st, factory: opts.Factory, ctx: ctx, cancel: cancel}, nil
}

// Run 的结果在容器关闭后才定案；清理失败会撤销产物引用并停止池接单。
// 不接纳的请求返回 ErrBusy/ErrClosed，执行结论由 RunResult.Status 表达。
func (p *Pool) Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error) {
	var res contract.RunResult
	if err := p.admit(); err != nil {
		return res, err
	}
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
	closeContainer := sync.OnceValue(func() error { return p.closeContainer(c) })
	// panic 时也要关闭容器并在清理失败时停止接单；正常路径显式处理关闭结果。
	defer closeContainer()
	res = runner.Run(runCtx, c, p.store, spec)
	if closeErr := closeContainer(); closeErr != nil {
		var rollback error
		for _, ref := range res.Artifacts {
			rollback = errors.Join(rollback, p.store.Delete(ref))
		}
		res.Artifacts, res.Outputs = nil, nil
		res.Status = contract.StatusInternalError
		res.Error = errors.Join(errors.New("容器清理失败"), closeErr, rollback).Error()
	}
	return res, nil
}

// Close 拒绝新请求，取消排队及在途执行，并等待各自的独立资源回收。
// 可重复调用；调用者须等它返回后再关闭 Factory 依赖的暂存根和共享 Store。
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

func (p *Pool) admit() error {
	p.mu.Lock()
	if p.closed {
		p.mu.Unlock()
		return ErrClosed
	}
	select {
	case p.admitted <- struct{}{}:
	default:
		p.mu.Unlock()
		return ErrBusy
	}
	// Add 与关闭检查共用锁，保证 Close 开始 Wait 后不会再登记新请求。
	p.wg.Add(1)
	p.mu.Unlock()
	return nil
}

// 无法确认资源回收时停止整个池，防止归还容量后继续启动新命令。
func (p *Pool) closeContainer(c container.Container) error {
	err := c.Close()
	if err != nil {
		p.mu.Lock()
		p.closed = true
		p.closeErr = errors.Join(p.closeErr, err)
		p.cancel()
		p.mu.Unlock()
	}
	return err
}
