package judge

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"time"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/platform/tracing"
	"cherry-oj/judge-engine/judge/internal/api"
	"cherry-oj/judge-engine/judge/internal/flow"
	"cherry-oj/judge-engine/judge/internal/node"
	"cherry-oj/judge-engine/judge/internal/sandboxclient"
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

// Run 启动判题服务并在 ctx 取消后收尾。配置加载、日志初始化与信号监听由调用方完成，
// 使本函数不依赖进程级状态，测试可以直接驱动它。
func Run(ctx context.Context, cfg config.Config, logger *slog.Logger) error {
	var err error
	if cfg.Judge.Node.Enabled {
		probeCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		cfg.Judge, err = node.ProbeEnvironment(probeCtx, cfg.Judge)
		cancel()
		if err != nil {
			logger.Error("judge.node.environment.probe.failed", "error", err)
			return err
		}
	}
	sandboxClient := sandboxclient.New(cfg.Judge.SandboxURL, cfg.Judge.SandboxTimeout.Std())
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
			return err
		}
		defer judgeNode.Close()
		service.config.EnvironmentFingerprint = judgeNode.Registration().EnvironmentFingerprint
		handler = judgeNode.Handler(handler)
	}
	srv := &http.Server{
		Addr:    cfg.Judge.HTTPAddr,
		Handler: tracing.Middleware(logger, handler),
	}

	// Bind before advertising the endpoint: an occupied port must never create an online node.
	listener, err := net.Listen("tcp", cfg.Judge.HTTPAddr)
	if err != nil {
		logger.Error("process.listen.failed", "error", err)
		return err
	}
	defer listener.Close()

	if judgeNode != nil {
		done := make(chan struct{})
		go func() { defer close(done); judgeNode.Run(ctx) }()
		defer func() { <-done }()
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

	var exitErr error
	select {
	case <-ctx.Done():
	case err := <-serveErr:
		if !errors.Is(err, http.ErrServerClosed) {
			logger.Error("process.serve.failed", "event", "process.serve.failed", "error", err)
			exitErr = err
		}
	}
	logger.Info("process.stopping", "event", "process.stopping")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("process.shutdown.failed", "event", "process.shutdown.failed", "error", err)
		exitErr = errors.Join(exitErr, err)
	}
	return exitErr
}
