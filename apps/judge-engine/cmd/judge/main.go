package main

import (
	"context"
	"errors"
	"flag"
	"log"
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
	configPath := flag.String("config", "", "配置文件路径；留空则只用默认值 + 环境变量")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("加载配置: %v", err)
	}

	sandboxClient := client.New(cfg.Judge.SandboxURL, cfg.Judge.SandboxTimeout.Std())
	service := &judgeService{
		sandbox: sandboxClient,
		config:  cfg.Judge,
	}
	srv := &http.Server{
		Addr:    cfg.Judge.HTTPAddr,
		Handler: api.New(service).Handler(),
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go func() {
		log.Printf("judge listening on %s (sandbox=%s)", cfg.Judge.HTTPAddr, cfg.Judge.SandboxURL)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("listen: %v", err)
		}
	}()

	<-ctx.Done()
	log.Println("shutting down...")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Printf("shutdown: %v", err)
	}
}
