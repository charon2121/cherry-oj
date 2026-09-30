package execution

import (
	"context"
	"errors"
	"log/slog"
	"net/http"

	"cherry-oj/judge-engine/execution/api"
	"cherry-oj/judge-engine/execution/pool"
	"cherry-oj/judge-engine/internal/platform/tracing"
)

// Run 启动执行服务并在 ctx 取消后收尾。配置加载、日志初始化与信号监听由调用方完成。
// 返回的 result 汇总收尾错误：任何一处回收没有确认，整个进程都应以失败退出。
func Run(ctx context.Context, cfg Config, logger *slog.Logger) (result error) {
	e, err := Open(cfg.Sandbox.engine(), logger)
	if err != nil {
		logger.Error("process.execution.init.failed", "error", err)
		return err
	}
	defer func() {
		if err := e.Close(); err != nil {
			logger.Error("process.execution.close.failed", "error", err)
			result = errors.Join(result, err)
		}
	}()
	return serve(ctx, newHTTPServer(cfg.Sandbox, e, logger), e.pool, cfg.Sandbox, logger)
}

// newHTTPServer 装配 HTTP 服务。期限覆盖请求读取和响应传输，不能用用户命令的墙钟限额替代。
// handler 的执行器是 Pool；每次执行在暂存根下创建自己的目录（见 backend.Isolated）。
func newHTTPServer(c Settings, e *Engine, logger *slog.Logger) *http.Server {
	srv := &http.Server{
		Addr:              c.HTTPAddr,
		ReadHeaderTimeout: httpReadHeaderTimeout,
		ReadTimeout:       httpReadTimeout,
		WriteTimeout:      httpWriteTimeout,
		IdleTimeout:       httpIdleTimeout,
		MaxHeaderBytes:    httpMaxHeaderBytes,
		Handler: api.New(e.pool, e.store, api.Options{
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
