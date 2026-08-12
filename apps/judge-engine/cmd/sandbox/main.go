package main

import (
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
	"runtime"
	"syscall"
	"time"
)

func main() {
	addr := flag.String("http-addr", "127.0.0.1:5050", "监听地址")
	parallelism := flag.Int("parallelism", runtime.NumCPU(), "最大并发执行数")
	flag.Parse()

	st, err := store.NewDiskStore()
	if err != nil {
		log.Fatalf("init store: %v", err)
	}

	p := pool.New(*parallelism, st)
	defer p.Close()

	srv := &http.Server{
		Addr:    *addr,
		Handler: api.New(p, st).Handler(),
	}

	// Ctrl-C / SIGTERM 时取消这个 ctx
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go func() {
		log.Printf("sandbox listening on %s (parallelism=%d)", *addr, *parallelism)
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
