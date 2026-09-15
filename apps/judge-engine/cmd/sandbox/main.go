package main

import (
	"context"
	"flag"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/platform/logging"
	"cherry-oj/judge-engine/sandbox"
)

func main() {
	os.Exit(run())
}

// run 用返回值退出，使资源清理的 defer 在 main 调用 os.Exit 前执行。
// 本文件只做参数解析与进程级装配；服务本身在 sandbox.Run。
func run() int {
	bootstrap := logging.Console("sandbox", os.Stderr)
	// flag 只选择配置文件，避免为每个设置再引入一套覆盖顺序；配置值由 Load 统一合并。
	configPath := flag.String("config", "", "配置文件路径；留空则只用默认值 + 环境变量")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		bootstrap.Error("process.config.load.failed", "event", "process.config.load.failed", "error", err)
		return 1
	}
	logger, logFiles, err := logging.New("sandbox", cfg.Logging.Path, cfg.Logging.Level)
	if err != nil {
		bootstrap.Error("process.log.init.failed", "event", "process.log.init.failed", "error", err)
		return 1
	}
	defer func() {
		if err := logFiles.Close(); err != nil {
			logger.Error("process.log.close.failed", "error", err)
		}
	}()
	slog.SetDefault(logger)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	if err := sandbox.Run(ctx, cfg, logger); err != nil {
		return 1
	}
	return 0
}
