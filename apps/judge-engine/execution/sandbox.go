package execution

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"runtime"
	"time"

	"cherry-oj/judge-engine/execution/api"
	"cherry-oj/judge-engine/execution/backend"
	"cherry-oj/judge-engine/execution/pool"
	"cherry-oj/judge-engine/execution/runner"
	"cherry-oj/judge-engine/execution/store"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/platform/tracing"
)

// Run 启动执行服务并在 ctx 取消后收尾。配置加载、日志初始化与信号监听由调用方完成。
// 返回的 result 汇总收尾错误：任何一处回收没有确认，整个进程都应以失败退出。
func Run(ctx context.Context, cfg Config, logger *slog.Logger) (result error) {
	st, err := newStore(cfg.Sandbox.Store)
	if err != nil {
		logger.Error("process.store.init.failed", "event", "process.store.init.failed", "error", err)
		return err
	}

	// defer 逆序释放：先结束执行池，再关闭暂存根，最后释放共享 Store。
	// 请求可能仍在回滚产物引用，不能先关闭 Store 或暂存根。
	defer func() {
		if err := st.Close(); err != nil {
			logger.Error("process.store.close.failed", "error", err)
			result = errors.Join(result, err)
		}
	}()
	executor, backendClose, err := selectBackend(cfg.Sandbox)
	if err != nil {
		logger.Error("process.backend.init.failed", "error", err)
		return err
	}
	defer func() {
		if err := backendClose(); err != nil {
			logger.Error("process.backend.close.failed", "error", err)
			result = errors.Join(result, err)
		}
	}()
	p, err := pool.New(runner.New(executor, st), pool.Options{Parallelism: cfg.Sandbox.Parallelism, QueueSize: cfg.Sandbox.QueueSize, Logger: logger})
	if err != nil {
		logger.Error("process.pool.init.failed", "error", err)
		return err
	}
	defer func() {
		if err := p.Close(); err != nil {
			logger.Error("process.pool.close.failed", "error", err)
			result = errors.Join(result, err)
		}
	}()
	if err := probeBackend(p, cfg.Sandbox.Backend, logger); err != nil {
		return err
	}
	defer startSweeper(st, logger)()
	return serve(ctx, newHTTPServer(cfg.Sandbox, p, st, logger), p, cfg.Sandbox, logger)
}

// probeBackend 用一次真实执行（命令 true）验证 linux 后端整条链可用，失败不开放 HTTP 端口。
func probeBackend(p *pool.Pool, name string, logger *slog.Logger) error {
	if name != backend.NameLinux {
		return nil
	}
	probe, probeErr := p.Run(context.Background(), contract.RunSpec{Command: []string{"true"}})
	if probeErr != nil || probe.Status != contract.StatusOK {
		logger.Error("process.backend.probe.failed", "error", probeErr, "result", probe)
		return errors.Join(fmt.Errorf("isolated backend startup probe failed: status=%s", probe.Status), probeErr)
	}
	return nil
}

// startSweeper 每分钟清理一次过期 blob，返回的函数停止它并等待退出。
func startSweeper(st managedStore, logger *slog.Logger) (stop func()) {
	gcCtx, gcCancel := context.WithCancel(context.Background())
	gcDone := make(chan struct{})
	go func() {
		defer close(gcDone)
		ticker := time.NewTicker(time.Minute)
		defer ticker.Stop()
		for {
			select {
			case <-gcCtx.Done():
				return
			case <-ticker.C:
				if e := st.Sweep(); e != nil {
					logger.Error("store.sweep.failed", "error", e)
				}
			}
		}
	}()
	return func() { gcCancel(); <-gcDone }
}

// newHTTPServer 装配 HTTP 服务。期限覆盖请求读取和响应传输，不能用用户命令的墙钟限额替代。
// handler 的执行器是 Pool；每次执行在暂存根下创建自己的目录（见 backend.Isolated）。
func newHTTPServer(c Settings, p *pool.Pool, st managedStore, logger *slog.Logger) *http.Server {
	srv := &http.Server{
		Addr:              c.HTTPAddr,
		ReadHeaderTimeout: httpReadHeaderTimeout,
		ReadTimeout:       httpReadTimeout,
		WriteTimeout:      httpWriteTimeout,
		IdleTimeout:       httpIdleTimeout,
		MaxHeaderBytes:    httpMaxHeaderBytes,
		Handler: api.New(p, st, api.Options{
			MaxBlobBytes:    c.Store.MaxBlobBytes,
			MaxRequestBytes: c.MaxRequestBytes,
			MaxConcurrent:   c.Parallelism + c.QueueSize + httpHeadroom,
			Isolation:       c.Backend,
		}).Handler(),
	}
	srv.Handler = tracing.Middleware(logger, srv.Handler)
	return srv
}

// serve 开放端口直到 ctx 取消或监听失败，然后按顺序收尾：先取消执行并确认回收，再停止 HTTP。
func serve(ctx context.Context, srv *http.Server, p *pool.Pool, c Settings, logger *slog.Logger) error {
	serveErr := make(chan error, 1)
	go func() {
		logger.Info("process.started",
			"event", "process.started",
			"http_addr", c.HTTPAddr,
			"parallelism", c.Parallelism,
		)
		serveErr <- srv.ListenAndServe()
	}()

	var exitErr error
	select {
	case <-ctx.Done():
	case err := <-serveErr:
		if !errors.Is(err, http.ErrServerClosed) {
			logger.Error("process.serve.failed", "event", "process.serve.failed", "error", err)
			exitErr = errors.Join(exitErr, err)
		}
	}
	logger.Info("process.stopping", "event", "process.stopping")

	if err := p.Close(); err != nil {
		logger.Error("process.pool.close.failed", "error", err)
		exitErr = errors.Join(exitErr, err)
	}
	// 执行已取消，HTTP 仍需写出失败结果；收尾期限不能继承已取消的信号上下文。
	shutCtx, cancel := context.WithTimeout(context.Background(), httpShutdownTimeout)
	defer cancel()
	if err := srv.Shutdown(shutCtx); err != nil {
		logger.Error("process.shutdown.failed", "event", "process.shutdown.failed", "error", err)
		exitErr = errors.Join(exitErr, err)
		srv.Close()
	}
	return exitErr
}

// Store生命周期由入口拥有，HTTP和Pool不擅自关闭共享Store。
type managedStore interface {
	store.Store
	io.Closer
	Sweep() error
}

func newStore(c Store) (managedStore, error) {
	return store.New(c.Root, store.Options{MaxBlobBytes: c.MaxBlobBytes, MaxTotalBytes: c.MaxTotalBytes, MaxEntries: c.MaxEntries, Retention: c.Retention.Std()})
}

// selectBackend 同时交付执行后端和服务级关闭函数；暂存根的锁跨请求持有。
// 隔离后端启动失败必须暴露错误，不能悄悄切到没有隔离保证的 devhost。
func selectBackend(c Settings) (backend.Backend, func() error, error) {
	switch c.Backend {
	case backend.NameDevHost:
		// 配置校验已经要求显式承认；这里再挡一次，避免绕过校验直接装配。
		if !c.AllowUnsafeBackend {
			return nil, nil, fmt.Errorf("the %s backend provides no isolation; it requires setting allowUnsafeBackend explicitly", backend.NameDevHost)
		}
		return backend.NewDevHost(), func() error { return nil }, nil
	case backend.NameLinux:
		if runtime.GOOS != "linux" || runtime.GOARCH != "amd64" {
			return nil, nil, fmt.Errorf("this platform does not support the Linux isolation backend")
		}
		if os.Geteuid() == 0 {
			return nil, nil, fmt.Errorf("the sandbox service must run as non-root; privilege is held only by the setuid executor")
		}
		b, err := backend.NewExecutor(c.ExecutorPath, c.BoxesRoot, c.Parallelism)
		if err != nil {
			return nil, nil, err
		}
		return b, b.Close, nil
	default:
		return nil, nil, fmt.Errorf("unknown backend: %s", c.Backend)
	}
}
