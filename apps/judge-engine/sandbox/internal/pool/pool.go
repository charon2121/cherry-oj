// Package pool 管理执行容量：接纳、排队、限制并发。它不创建工作区，也不回收产物。
package pool

import (
	"context"
	"errors"
	"fmt"
	"sync"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/internal/backend"
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

type Options struct {
	Parallelism int
	QueueSize   int
}

// Pool 分别限制已接纳请求与实际执行，避免排队请求无限占用服务资源。
// 后端可并发使用：每次执行在自己的工作区里完成，池只负责有多少次可以同时进行。
type Pool struct {
	sem      chan struct{} // 持有到 Execute 返回为止，防止清理中的执行与新任务重叠。
	admitted chan struct{} // 同时计入执行中和排队中的请求。
	store    store.Store
	backend  backend.Backend
	ctx      context.Context
	cancel   context.CancelFunc
	mu       sync.Mutex
	closed   bool
	wg       sync.WaitGroup
	closeErr error
}

func New(st store.Store, b backend.Backend, opts Options) (*Pool, error) {
	if st == nil || b == nil || opts.Parallelism <= 0 || opts.Parallelism > maxParallelism ||
		opts.QueueSize <= 0 || opts.QueueSize > maxQueueSize {
		return nil, fmt.Errorf("pool requires a store, a backend and bounded positive parallelism/queueSize")
	}
	ctx, cancel := context.WithCancel(context.Background())
	return &Pool{sem: make(chan struct{}, opts.Parallelism),
		admitted: make(chan struct{}, opts.Parallelism+opts.QueueSize),
		store:    st, backend: b, ctx: ctx, cancel: cancel}, nil
}

// Run 排队、取得执行名额并执行一次命令。
// 不接纳的请求返回 ErrBusy/ErrClosed，执行结论由 RunResult.Status 表达。
// 回收未确认时停止整个池：无法判定残留资源，就不能把容量归还给下一条命令。
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
	res, cleanupErr := runner.Run(runCtx, p.backend, p.store, spec)
	if cleanupErr != nil {
		p.poison(cleanupErr)
	}
	return res, nil
}

// Close 拒绝新请求，取消排队及在途执行，并等待各自的独立资源回收。
// 可重复调用；调用者须等它返回后再关闭后端依赖的暂存根和共享 Store。
func (p *Pool) Close() error {
	p.mu.Lock()
	if !p.closed {
		p.closed = true
		p.cancel()
	}
	p.mu.Unlock()
	p.wg.Wait()
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

// poison 让池停止接单。回收未确认意味着残留的进程或挂载可能与后续执行重叠，
// 继续启动新命令会把一次故障扩散成一串。
func (p *Pool) poison(err error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.closed = true
	p.closeErr = errors.Join(p.closeErr, err)
	p.cancel()
}
