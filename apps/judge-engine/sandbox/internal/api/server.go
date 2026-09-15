package api

import (
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/internal/store"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
)

// Executor 返回前必须完成影响结果的回收；HTTP 层无法补救已发布结果后的清理失败。
// cmd/sandbox 将它绑定到 pool.Pool，普通命令失败通过 RunResult.Status 返回。
type Executor interface {
	Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error)
}

// Options 约束 HTTP 请求的资源占用，与单次命令的 Limits 分开。
// 容量字段 <=0 时 New 使用默认值；Isolation 只用于报告已配置的后端。
type Options struct {
	MaxBlobBytes    int64
	MaxRequestBytes int64
	MaxConcurrent   int
	Isolation       string
}

const defaultMaxBlobBytes = 64 << 20

// Server 借用执行器和 Store；它们的服务级生命周期由组装入口管理。
type Server struct {
	exec     Executor
	store    store.Store
	opts     Options
	requests chan struct{}
}

// New 不启动监听或接管依赖的关闭；调用者先准备好执行器和 Store。
func New(exec Executor, st store.Store, opts Options) *Server {
	if opts.MaxBlobBytes <= 0 {
		opts.MaxBlobBytes = defaultMaxBlobBytes
	}
	if opts.MaxRequestBytes <= 0 {
		opts.MaxRequestBytes = 2 << 20
	}
	if opts.MaxConcurrent <= 0 {
		opts.MaxConcurrent = 16
	}
	if opts.Isolation == "" {
		opts.Isolation = "unconfigured"
	}
	return &Server{exec: exec, store: st, opts: opts, requests: make(chan struct{}, opts.MaxConcurrent)}
}

// Handler 的容量覆盖上传、下载和执行，避免慢传输绕过执行池的并发限制。
// 超额立即拒绝；需要排队的命令由 Pool 在自己的有界队列中管理。
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /version", s.handleVersion)
	mux.HandleFunc("POST /run", s.handleRun)
	mux.HandleFunc("POST /blobs", s.handleBlobPut)
	mux.HandleFunc("GET /blobs/{ref}", s.handleBlobGet)
	mux.HandleFunc("DELETE /blobs/{ref}", s.handleBlobDelete)
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		select {
		case s.requests <- struct{}{}:
			defer func() { <-s.requests }()
			mux.ServeHTTP(w, r)
		default:
			writeError(w, http.StatusServiceUnavailable, fmt.Errorf("sandbox HTTP容量已满"))
		}
	})
}

func (s *Server) handleVersion(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"name":      "cherry-oj-sandbox",
		"version":   "0.1.0-mvp",
		"isolation": s.opts.Isolation,
	})
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, code int, err error) {
	writeJSON(w, code, map[string]string{"error": err.Error()})
}
