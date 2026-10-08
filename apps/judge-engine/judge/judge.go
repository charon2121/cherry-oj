package judge

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"time"

	"cherry-oj/judge-engine/execution"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/platform/tracing"
	"cherry-oj/judge-engine/judge/api"
	judgeconfig "cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/flow"
	"cherry-oj/judge-engine/judge/node"
	"cherry-oj/judge-engine/judge/testcase"
)

// HTTP 连接的防护期限，与 sandbox 取值一致；不含写期限，理由见 Run。
const (
	httpReadHeaderTimeout = 5 * time.Second
	httpIdleTimeout       = 30 * time.Second
	httpMaxHeaderBytes    = 16 << 10
)

// judgeService 把 HTTP API 的单次判题入口接到判题编排。
// 依赖保留为 flow.Sandbox，既能接真实客户端，也不把传输实现泄漏给 API 层。
type judgeService struct {
	sandbox flow.Sandbox
	config  judgeconfig.Settings
	logger  *slog.Logger
}

func (s *judgeService) Judge(ctx context.Context, req contract.JudgeRequest) contract.JudgeResult {
	result := flow.Judge(ctx, s.sandbox, s.config, req, s.logger)
	if result.Verdict == contract.VerdictSE {
		// SE 是平台自己的问题（sandbox 不可用、测试数据损坏……），原因只写在响应的 message 里；
		// 不在这里留痕，排查就只能去调用方翻响应。WA、TLE 等正常结论不记。
		s.logger.Warn("judge.result.system_error", "submissionId", req.SubmissionID,
			"problemId", req.ProblemID, "testDataLocation", req.TestDataLocation, "reason", systemErrorReason(result))
	}
	return result
}

// systemErrorReason 取整次判题的 message；为空时取第一个判成 SE 的测试点的 message。
func systemErrorReason(r contract.JudgeResult) string {
	if r.Message != "" {
		return r.Message
	}
	for _, c := range r.TestcaseResults {
		if c.Verdict == contract.VerdictSE {
			return fmt.Sprintf("case %d: %s", c.Idx, c.Message)
		}
	}
	return ""
}

// Run 启动判题服务并在 ctx 取消后收尾。配置加载、日志初始化与信号监听由调用方完成，
// 使本函数不依赖进程级状态，测试可以直接驱动它。
func Run(ctx context.Context, cfg Config, logger *slog.Logger) (result error) {
	engine, err := execution.Open(cfg.Execution, logger)
	if err != nil {
		logger.Error("process.execution.init.failed", "error", err)
		return err
	}
	defer func() {
		if err := engine.Close(); err != nil {
			logger.Error("process.execution.close.failed", "error", err)
			result = errors.Join(result, err)
		}
	}()
	if cfg.Judge.Node.Enabled {
		checkCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		err = node.Preflight(checkCtx, cfg.Judge, engine)
		cancel()
		if err != nil {
			logger.Error("judge.node.preflight.failed", "error", err)
			return err
		}
	}
	// 测试数据的本地副本放在这个进程独占的目录里；先清掉上一个进程崩溃时遗留的副本。
	if err := testcase.PrepareWorkRoot(cfg.Judge.Testdata.WorkRoot); err != nil {
		logger.Error("process.testdata.init.failed", "error", err)
		return err
	}
	service := &judgeService{
		sandbox: engine,
		config:  cfg.Judge,
		logger:  logger,
	}
	handler := api.New(service).Handler()
	var judgeNode *node.Node
	if cfg.Judge.Node.Enabled {
		judgeNode, err = node.New(cfg.Judge, logger)
		if err != nil {
			logger.Error("judge.node.init.failed", "error", err)
			return err
		}
	}
	// 只限制读请求头与空闲连接：一次判题（编译加逐点运行）可能持续数分钟，不能设写期限。
	srv := &http.Server{
		Addr:              cfg.Judge.HTTPAddr,
		Handler:           tracing.Middleware(logger, handler),
		ReadHeaderTimeout: httpReadHeaderTimeout,
		IdleTimeout:       httpIdleTimeout,
		MaxHeaderBytes:    httpMaxHeaderBytes,
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
	logger.Info("process.started", "event", "process.started", "http_addr", cfg.Judge.HTTPAddr,
		"isolation", cfg.Execution.Backend)
	return serve(ctx, srv, listener, runNode, engine.Stopped(), logger)
}

// 服务异常退出与外部取消都必须结束心跳；等待之前先取消本服务拥有的生命周期。
//
// 执行层停止接单（stopped 关闭）也结束服务：回收未确认意味着这台节点已经不能安全执行，
// 以失败退出、停止心跳，比继续在线把每次提交都判成 SE 更早暴露问题。
func serve(ctx context.Context, srv *http.Server, listener net.Listener, runNode func(context.Context), stopped <-chan struct{}, logger *slog.Logger) error {
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
	case <-stopped:
		exitErr = errors.New("execution layer stopped accepting work: reclaim was not confirmed")
		logger.Error("process.execution.stopped", "event", "process.execution.stopped", "error", exitErr)
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
