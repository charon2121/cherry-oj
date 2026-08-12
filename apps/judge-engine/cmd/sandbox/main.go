package main

import (
	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/sandbox/api"
	"cherry-oj/judge-engine/internal/sandbox/pool"
	"cherry-oj/judge-engine/internal/sandbox/store"
	"context"
	"errors"
	"flag"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	// 只有这一个 flag：配置文件路径。
	// 每个配置项都配一个 flag 的话就有三套真源（flag / YAML / 环境变量），
	// 谁覆盖谁得记一张表。一个 -config 指路，其余走「默认值 → YAML → 环境变量」。
	configPath := flag.String("config", "", "配置文件路径；留空则只用默认值 + 环境变量")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		// 配错了就别启动。一个 maxBlobBytes: 0 的配置能让服务正常起来、
		// 然后每次上传都失败——宁可起不来，也别悄悄跑错。
		log.Fatalf("加载配置: %v", err)
	}

	st, err := newStore(cfg.Sandbox.Store)
	if err != nil {
		log.Fatalf("init store: %v", err)
	}

	p := pool.New(cfg.Sandbox.Parallelism, st)
	defer p.Close()

	srv := &http.Server{
		Addr: cfg.Sandbox.HTTPAddr,
		Handler: api.New(p, st, api.Options{
			MaxBlobBytes: cfg.Sandbox.Store.MaxBlobBytes,
		}).Handler(),
	}

	// Ctrl-C / SIGTERM 时取消这个 ctx
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go func() {
		log.Printf("sandbox listening on %s (parallelism=%d)",
			cfg.Sandbox.HTTPAddr, cfg.Sandbox.Parallelism)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("listen: %v", err)
		}
	}()

	<-ctx.Done() // 阻塞到收到信号
	log.Println("shutting down...")

	// 给正在跑的请求 10 秒收尾
	shutCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutCtx); err != nil {
		log.Printf("shutdown: %v", err)
	}
}

// newStore：root 留空表示「自动探测」，不是「用当前目录」。
func newStore(c config.StoreConfig) (store.Store, error) {
	if c.Root == "" {
		return store.NewDiskStore()
	}
	return store.NewDiskStoreWithRoot(c.Root)
}
