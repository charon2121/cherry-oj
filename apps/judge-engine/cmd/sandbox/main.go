package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"runtime"
	"syscall"
	"time"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
	enginelog "cherry-oj/judge-engine/internal/logging"
	"cherry-oj/judge-engine/internal/sandbox/api"
	"cherry-oj/judge-engine/internal/sandbox/container"
	"cherry-oj/judge-engine/internal/sandbox/pool"
	"cherry-oj/judge-engine/internal/sandbox/store"
	"cherry-oj/judge-engine/internal/tracecontext"
)

func main() {
	os.Exit(run())
}

func run() (exitCode int) {
	bootstrapLogger := enginelog.Console("sandbox", os.Stderr)
	// 只有这一个 flag：配置文件路径。
	// 每个配置项都配一个 flag 的话就有三套真源（flag / YAML / 环境变量），
	// 谁覆盖谁得记一张表。一个 -config 指路，其余走「默认值 → YAML → 环境变量」。
	configPath := flag.String("config", "", "配置文件路径；留空则只用默认值 + 环境变量")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		// 配错了就别启动。一个 maxBlobBytes: 0 的配置能让服务正常起来、
		// 然后每次上传都失败——宁可起不来，也别悄悄跑错。
		bootstrapLogger.Error("process.config.load.failed", "event", "process.config.load.failed", "error", err)
		return 1
	}
	logger, logFiles, err := enginelog.New("sandbox", cfg.Logging.Path, cfg.Logging.Level)
	if err != nil {
		bootstrapLogger.Error("process.log.init.failed", "event", "process.log.init.failed", "error", err)
		return 1
	}
	defer func() {
		if err := logFiles.Close(); err != nil {
			logger.Error("process.log.close.failed", "error", err)
		}
	}()
	slog.SetDefault(logger)

	st, err := newStore(cfg.Sandbox.Store)
	if err != nil {
		logger.Error("process.store.init.failed", "event", "process.store.init.failed", "error", err)
		return 1
	}

	defer func() {
		if err := st.Close(); err != nil {
			logger.Error("process.store.close.failed", "error", err)
			exitCode = 1
		}
	}()
	factory, backendClose, err := backend(cfg.Sandbox)
	if err != nil {
		logger.Error("process.backend.init.failed", "error", err)
		return 1
	}
	defer func() {
		if err := backendClose(); err != nil {
			logger.Error("process.backend.close.failed", "error", err)
			exitCode = 1
		}
	}()
	p, err := pool.New(st, pool.Options{Parallelism: cfg.Sandbox.Parallelism, QueueSize: cfg.Sandbox.QueueSize, Factory: factory})
	if err != nil {
		logger.Error("process.pool.init.failed", "error", err)
		return 1
	}
	defer func() {
		if err := p.Close(); err != nil {
			logger.Error("process.pool.close.failed", "error", err)
			exitCode = 1
		}
	}()
	if cfg.Sandbox.Backend == "linux" {
		// 用完整Container链验证可用性，失败不开放HTTP端口。
		result, e := p.Run(context.Background(), contract.RunSpec{Command: []string{"true"}})
		if e != nil || result.Status != contract.StatusOK {
			logger.Error("process.backend.probe.failed", "error", e, "result", result)
			return 1
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

	srv := &http.Server{
		Addr:              cfg.Sandbox.HTTPAddr,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      160 * time.Second,
		IdleTimeout:       30 * time.Second,
		MaxHeaderBytes:    16 << 10,
		Handler: api.New(p, st, api.Options{
			MaxBlobBytes:    cfg.Sandbox.Store.MaxBlobBytes,
			MaxRequestBytes: cfg.Sandbox.MaxRequestBytes,
			MaxConcurrent:   cfg.Sandbox.Parallelism + cfg.Sandbox.QueueSize + 4,
			Isolation:       cfg.Sandbox.Backend,
		}).Handler(),
	}
	srv.Handler = tracecontext.Middleware(logger, srv.Handler)

	// Ctrl-C / SIGTERM 时取消这个 ctx
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	serveErr := make(chan error, 1)
	go func() {
		logger.Info("process.started",
			"event", "process.started",
			"http_addr", cfg.Sandbox.HTTPAddr,
			"parallelism", cfg.Sandbox.Parallelism,
		)
		serveErr <- srv.ListenAndServe()
	}()

	exitCode = 0
	select {
	case <-ctx.Done():
	case err := <-serveErr:
		if !errors.Is(err, http.ErrServerClosed) {
			logger.Error("process.serve.failed", "event", "process.serve.failed", "error", err)
			exitCode = 1
		}
	}
	logger.Info("process.stopping", "event", "process.stopping")

	// 先取消执行并确认回收，再停止HTTP和Store。
	if err := p.Close(); err != nil {
		logger.Error("process.pool.close.failed", "error", err)
		exitCode = 1
	}
	// 给HTTP传输10秒收尾
	shutCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutCtx); err != nil {
		logger.Error("process.shutdown.failed", "event", "process.shutdown.failed", "error", err)
		exitCode = 1
		srv.Close()
	}
	return exitCode
}

// Store生命周期由入口拥有，HTTP和Pool不擅自关闭共享Store。
type managedStore interface {
	store.Store
	io.Closer
	Sweep() error
}

func newStore(c config.StoreConfig) (managedStore, error) {
	return store.New(c.Root, store.Options{MaxBlobBytes: c.MaxBlobBytes, MaxTotalBytes: c.MaxTotalBytes, MaxEntries: c.MaxEntries, Retention: c.Retention.Std()})
}
func backend(c config.SandboxConfig) (func() (container.Container, error), func() error, error) {
	switch c.Backend {
	case "trusted-host":
		return func() (container.Container, error) { return container.NewHost() }, func() error { return nil }, nil
	case "linux":
		if runtime.GOOS != "linux" || runtime.GOARCH != "amd64" {
			return nil, nil, fmt.Errorf("当前平台不支持Linux隔离后端")
		}
		if os.Geteuid() == 0 {
			return nil, nil, fmt.Errorf("sandbox服务必须非root运行，特权仅由helper持有")
		}
		w, err := container.OpenWorkspace(c.WorkspaceRoot)
		if err != nil {
			return nil, nil, err
		}
		return func() (container.Container, error) { return w.New(c.HelperSocket) }, w.Close, nil
	default:
		return nil, nil, fmt.Errorf("未知后端: %s", c.Backend)
	}
}
