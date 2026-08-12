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

type Server struct {
	exec  Executor
	store store.Store
}

func New(exec Executor, st store.Store) *Server {
	return &Server{exec: exec, store: st}
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
