package api

import (
	"context"
	"encoding/json"
	"net/http"

	"cherry-oj/judge-engine/internal/contract"
)

// Judger 是 HTTP 层完成一次请求所需要的能力。
// sandbox client 与进程配置由 cmd/judge 在启动时绑定，不进入每次请求的方法签名。
type Judger interface {
	Judge(ctx context.Context, req contract.JudgeRequest) contract.JudgeResult
}

type Server struct {
	judger Judger
}

func New(judger Judger) *Server {
	return &Server{judger: judger}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /version", s.handleVersion)
	mux.HandleFunc("POST /judge", s.handleJudge)
	return mux
}

func (s *Server) handleVersion(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{
		"name":    "cherry-oj-judge",
		"version": "0.1.0-mvp",
	})
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeError(w http.ResponseWriter, status int, err error) {
	writeJSON(w, status, map[string]string{"error": err.Error()})
}
