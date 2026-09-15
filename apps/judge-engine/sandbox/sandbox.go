package sandbox

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

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/platform/tracing"
	"cherry-oj/judge-engine/sandbox/internal/api"
	"cherry-oj/judge-engine/sandbox/internal/backend"
	"cherry-oj/judge-engine/sandbox/internal/pool"
	"cherry-oj/judge-engine/sandbox/internal/store"
	"cherry-oj/judge-engine/sandbox/internal/workspace"
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
	p, err := pool.New(st, executor, pool.Options{Parallelism: cfg.Sandbox.Parallelism, QueueSize: cfg.Sandbox.QueueSize})
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
	if cfg.Sandbox.Backend == backend.NameLinux {
		// 用完整Container链验证可用性，失败不开放HTTP端口。
		probe, probeErr := p.Run(context.Background(), contract.RunSpec{Command: []string{"true"}})
		if probeErr != nil || probe.Status != contract.StatusOK {
			logger.Error("process.backend.probe.failed", "error", probeErr, "result", probe)
			return errors.Join(fmt.Errorf("isolated backend startup probe failed: status=%s", probe.Status), probeErr)
		}
	}
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
	defer func() { gcCancel(); <-gcDone }()

	// HTTP 期限覆盖请求读取和响应传输，不能用用户命令的墙钟限额替代。
	// Executor 的实际实现是 Pool；Linux Factory 每次创建独立的 helper 客户端工作区。
	srv := &http.Server{
		Addr:              cfg.Sandbox.HTTPAddr,
		ReadHeaderTimeout: httpReadHeaderTimeout,
		ReadTimeout:       httpReadTimeout,
		WriteTimeout:      httpWriteTimeout,
		IdleTimeout:       httpIdleTimeout,
		MaxHeaderBytes:    httpMaxHeaderBytes,
		Handler: api.New(p, st, api.Options{
			MaxBlobBytes:    cfg.Sandbox.Store.MaxBlobBytes,
			MaxRequestBytes: cfg.Sandbox.MaxRequestBytes,
			MaxConcurrent:   cfg.Sandbox.Parallelism + cfg.Sandbox.QueueSize + 4,
			Isolation:       cfg.Sandbox.Backend,
		}).Handler(),
	}
	srv.Handler = tracing.Middleware(logger, srv.Handler)

	serveErr := make(chan error, 1)
	go func() {
		logger.Info("process.started",
			"event", "process.started",
			"http_addr", cfg.Sandbox.HTTPAddr,
			"parallelism", cfg.Sandbox.Parallelism,
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

	// 先取消执行并确认回收，再停止HTTP和Store。
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
	return errors.Join(result, exitErr)
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
			return nil, nil, fmt.Errorf("the sandbox service must run as non-root; privilege is held only by the helper")
		}
		w, err := workspace.OpenWorkspace(c.WorkspaceRoot)
		if err != nil {
			return nil, nil, err
		}
		b, err := backend.NewIsolated(c.HelperSocket, w)
		if err != nil {
			return nil, nil, errors.Join(err, w.Close())
		}
		return b, w.Close, nil
	default:
		return nil, nil, fmt.Errorf("unknown backend: %s", c.Backend)
	}
}
