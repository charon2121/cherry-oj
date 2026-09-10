package main

import (
	"context"
	"errors"
	"flag"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/judge/api"
	"cherry-oj/judge-engine/internal/judge/client"
	"cherry-oj/judge-engine/internal/judge/flow"
	"cherry-oj/judge-engine/internal/judge/node"
	enginelog "cherry-oj/judge-engine/internal/logging"
	"cherry-oj/judge-engine/internal/tracecontext"
)

// judgeService 把 HTTP API 的单次判题入口接到判题编排。
// 依赖保留为 flow.Sandbox，既能接真实客户端，也不把传输实现泄漏给 API 层。
type judgeService struct {
	sandbox flow.Sandbox
	config  config.JudgeConfig
}

func (s *judgeService) Judge(ctx context.Context, req contract.JudgeRequest) contract.JudgeResult {
	return flow.Judge(ctx, s.sandbox, s.config, req)
}

func main() {
	os.Exit(run())
}

func run() int {
	bootstrapLogger := enginelog.Console("judge", os.Stderr)
	configPath := flag.String("config", "", "配置文件路径；留空则只用默认值 + 环境变量")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		bootstrapLogger.Error("process.config.load.failed", "event", "process.config.load.failed", "error", err)
		return 1
	}
	logger, logFiles, err := enginelog.New("judge", cfg.Logging.Path, cfg.Logging.Level)
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

	if cfg.Judge.Node.Enabled {
		probeCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		cfg.Judge, err = node.ProbeEnvironment(probeCtx, cfg.Judge)
		cancel()
		if err != nil {
			logger.Error("judge.node.environment.probe.failed", "error", err)
			return 1
		}
	}
	sandboxClient := client.New(cfg.Judge.SandboxURL, cfg.Judge.SandboxTimeout.Std())
	service := &judgeService{
		sandbox: sandboxClient,
		config:  cfg.Judge,
	}
	handler := api.New(service).Handler()
	var judgeNode *node.Node
	if cfg.Judge.Node.Enabled {
		judgeNode, err = node.New(cfg.Judge, logger)
		if err != nil {
			logger.Error("judge.node.init.failed")
			return 1
		}
		defer judgeNode.Close()
		service.config.EnvironmentFingerprint = judgeNode.Registration().EnvironmentFingerprint
		handler = judgeNode.Handler(handler)
	}
	srv := &http.Server{
		Addr:    cfg.Judge.HTTPAddr,
		Handler: tracecontext.Middleware(logger, handler),
	}

	// Bind before advertising the endpoint: an occupied port must never create an online node.
	listener, err := net.Listen("tcp", cfg.Judge.HTTPAddr)
	if err != nil {
		logger.Error("process.listen.failed", "error", err)
		return 1
	}
	defer listener.Close()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	if judgeNode != nil {
		done := make(chan struct{})
		go func() { defer close(done); judgeNode.Run(ctx) }()
		defer func() { stop(); <-done }()
	}

	serveErr := make(chan error, 1)
	go func() {
		logger.Info("process.started",
			"event", "process.started",
			"http_addr", cfg.Judge.HTTPAddr,
			"sandbox_url", cfg.Judge.SandboxURL,
		)
		serveErr <- srv.Serve(listener)
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

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("process.shutdown.failed", "event", "process.shutdown.failed", "error", err)
		exitCode = 1
	}
	return exitCode
}
