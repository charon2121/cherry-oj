package pool

import (
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/container"
	"cherry-oj/judge-engine/internal/sandbox/runner"
	"cherry-oj/judge-engine/internal/sandbox/store"
	"context"
	"fmt"
	"runtime"
)

type Pool struct {
	sem        chan struct{}
	containers chan container.Container
	store      store.Store
}

func New(parallelism int, st store.Store) *Pool {
	// 默认使用 CPU 核心数作为并发数
	if parallelism <= 0 {
		parallelism = runtime.NumCPU()
	}
	return &Pool{
		sem:        make(chan struct{}, parallelism),
		containers: make(chan container.Container, parallelism),
		store:      st,
	}
}

func (p *Pool) Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error) {
	select {
	case <-ctx.Done():
		return contract.RunResult{}, ctx.Err()
	case p.sem <- struct{}{}:
		defer func() { <-p.sem }()
	}

	c, err := p.get(ctx)
	if err != nil {
		return contract.RunResult{}, fmt.Errorf("获取容器失败: %w", err)
	}

	defer p.put(c)
	return runner.Run(ctx, c, p.store, spec), nil
}

func (p *Pool) get(ctx context.Context) (container.Container, error) {
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	case c := <-p.containers:
		return c, nil
	default:
		return container.NewHost() // 池子空则现造
	}
}

func (p *Pool) put(c container.Container) error {
	if err := c.Reset(); err != nil {
		c.Close()
		return fmt.Errorf("重置容器失败: %w", err)
	}
	select {
	case p.containers <- c:
		return nil
	default:
		return c.Close()
	}
}

// Close 关闭池中的所有闲置容器。调用前应确保没有请求在跑。
func (p *Pool) Close() error {
	var first error
	for {
		select {
		case c := <-p.containers:
			if err := c.Close(); err != nil && first == nil {
				first = err
			}
		default:
			return first
		}
	}
}
