package judge

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/platform/tracing"
	"cherry-oj/judge-engine/judge/internal/api"
	judgeconfig "cherry-oj/judge-engine/judge/internal/config"
	"cherry-oj/judge-engine/judge/internal/flow"
	"cherry-oj/judge-engine/judge/internal/node"
	"cherry-oj/judge-engine/judge/internal/sandboxclient"
)

// judgeService 把 HTTP API 的单次判题入口接到判题编排。
// 依赖保留为 flow.Sandbox，既能接真实客户端，也不把传输实现泄漏给 API 层。
type judgeService struct {
	sandbox flow.Sandbox
	config  judgeconfig.Settings
}

func (s *judgeService) Judge(ctx context.Context, req contract.JudgeRequest) contract.JudgeResult {
	return flow.Judge(ctx, s.sandbox, s.config, req)
}

// Run 启动判题服务并在 ctx 取消后收尾。配置加载、日志初始化与信号监听由调用方完成，
// 使本函数不依赖进程级状态，测试可以直接驱动它。
func Run(ctx context.Context, cfg Config, logger *slog.Logger) error {
	// 配置、环境、身份是三个值：配置加载后只读，环境由探测得到，身份由两者推出。
	var err error
	sandboxClient := sandboxclient.New(cfg.Judge.SandboxURL, cfg.Judge.SandboxTimeout.Std())
	env := node.DeclaredEnvironment(cfg.Judge)
	if cfg.Judge.Node.Enabled {
		probeCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		env, err = node.ProbeEnvironment(probeCtx, cfg.Judge, sandboxClient)
		cancel()
		if err != nil {
			logger.Error("judge.node.environment.probe.failed", "error", err)
			return err
		}
	}
	service := &judgeService{
		sandbox: sandboxClient,
		config:  cfg.Judge,
	}
	handler := api.New(service).Handler()
	var judgeNode *node.Node
	if cfg.Judge.Node.Enabled {
		judgeNode, err = node.New(cfg.Judge, env, logger)
		if err != nil {
			logger.Error("judge.node.init.failed")
			return err
		}
		defer judgeNode.Close()
		// 指纹以注册身份为准；配置里的声明值只在未启用节点链路时使用。
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

	var runNode func(context.Context)
	if judgeNode != nil {
		runNode = judgeNode.Run
	}
	logger.Info("process.started", "event", "process.started", "http_addr", cfg.Judge.HTTPAddr, "sandbox_url", cfg.Judge.SandboxURL)
	return serve(ctx, srv, listener, runNode, logger)
}

// 服务异常退出与外部取消都必须结束心跳；等待之前先取消本服务拥有的生命周期。
func serve(ctx context.Context, srv *http.Server, listener net.Listener, runNode func(context.Context), logger *slog.Logger) error {
	ctx, stop := context.WithCancel(ctx)
	defer stop()
	if runNode != nil {
		done := make(chan struct{})
		go func() { defer close(done); runNode(ctx) }()
		defer func() { stop(); <-done }()
	}

	serveErr := make(chan error, 1)
	go func() {
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
	stop()
	logger.Info("process.stopping", "event", "process.stopping")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("process.shutdown.failed", "event", "process.shutdown.failed", "error", err)
		exitErr = errors.Join(exitErr, err)
	}
	return exitErr
}
