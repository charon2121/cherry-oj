package main

import (
	"context"
	"errors"
	"flag"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"cherry-oj/judge-engine/internal/config"
	enginelog "cherry-oj/judge-engine/internal/logging"
	"cherry-oj/judge-engine/internal/sandbox/api"
	"cherry-oj/judge-engine/internal/sandbox/pool"
	"cherry-oj/judge-engine/internal/sandbox/store"
	"cherry-oj/judge-engine/internal/tracecontext"
)

func main() {
	os.Exit(run())
}

func run() int {
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

	p := pool.New(cfg.Sandbox.Parallelism, st)
	defer p.Close()

	srv := &http.Server{
		Addr: cfg.Sandbox.HTTPAddr,
		Handler: api.New(p, st, api.Options{
			MaxBlobBytes: cfg.Sandbox.Store.MaxBlobBytes,
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

	exitCode := 0
	select {
	case <-ctx.Done():
	case err := <-serveErr:
		if !errors.Is(err, http.ErrServerClosed) {
			logger.Error("process.serve.failed", "event", "process.serve.failed", "error", err)
			exitCode = 1
		}
	}
	logger.Info("process.stopping", "event", "process.stopping")

	// 给正在跑的请求 10 秒收尾
	shutCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutCtx); err != nil {
		logger.Error("process.shutdown.failed", "event", "process.shutdown.failed", "error", err)
		exitCode = 1
	}
	return exitCode
}

// newStore：root 留空表示「自动探测」，不是「用当前目录」。
func newStore(c config.StoreConfig) (store.Store, error) {
	if c.Root == "" {
		return store.NewDiskStore()
	}
	return store.NewDiskStoreWithRoot(c.Root)
}
