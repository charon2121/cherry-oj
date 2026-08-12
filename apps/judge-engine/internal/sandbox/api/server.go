package api

import (
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/store"
	"context"
	"encoding/json"
	"net/http"
)

type Executor interface {
	Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error)
}

// Options 是 api 层的运行参数。
//
// 用结构体而不是裸参数，是为了让调用处自解释：
// api.New(p, st, api.Options{MaxBlobBytes: ...}) 一眼看懂，
// api.New(p, st, 67108864) 则要回来翻签名。
type Options struct {
	// MaxBlobBytes：POST /blobs 单次上传上限。<=0 用默认值。
	MaxBlobBytes int64
}

const defaultMaxBlobBytes = 64 << 20

type Server struct {
	exec  Executor
	store store.Store
	opts  Options
}

func New(exec Executor, st store.Store, opts Options) *Server {
	if opts.MaxBlobBytes <= 0 { // 又一次零值兜底：没配 ≠ 不许上传
		opts.MaxBlobBytes = defaultMaxBlobBytes
	}
	return &Server{exec: exec, store: st, opts: opts}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /version", s.handleVersion)
	mux.HandleFunc("POST /run", s.handleRun)
	mux.HandleFunc("POST /blobs", s.handleBlobPut)
	mux.HandleFunc("GET /blobs/{ref}", s.handleBlobGet)
	mux.HandleFunc("DELETE /blobs/{ref}", s.handleBlobDelete)
	return mux
}

func (s *Server) handleVersion(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"name":      "cherry-oj-sandbox",
		"version":   "0.1.0-mvp",
		"isolation": "host",
	})
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code) // ★ 必须在写 body 之前，且只能调一次
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, code int, err error) {
	writeJSON(w, code, map[string]string{"error": err.Error()})
}
