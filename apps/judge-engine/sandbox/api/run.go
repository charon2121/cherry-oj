package api

import (
	"cherry-oj/judge-engine/internal/contract"

	"encoding/json"
	"errors"
	"io"
	"net/http"
)

// handleRun 只判定请求能否被理解；预算耗尽、非零退出等执行结论交给 Executor。
func (s *Server) handleRun(w http.ResponseWriter, r *http.Request) {
	var spec contract.RunSpec
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, s.opts.MaxRequestBytes))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&spec); err != nil {
		writeError(w, http.StatusBadRequest, err)
		return
	}

	// 第一次 Decode 只读取一个 JSON 值，必须再读到 EOF 才能拒绝拼接的第二个请求。
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		writeError(w, http.StatusBadRequest, errors.New("JSON has trailing content"))
		return
	}
	if err := spec.Limits.Validate(); err != nil {
		writeError(w, http.StatusBadRequest, err)
		return
	}
	if len(spec.Command) == 0 {
		writeError(w, http.StatusBadRequest, errors.New("command must not be empty"))
		return
	}

	res, err := s.exec.Run(r.Context(), spec)
	if err != nil {
		var unavailable interface{ Unavailable() bool }
		if errors.As(err, &unavailable) && unavailable.Unavailable() {
			writeError(w, http.StatusServiceUnavailable, err)
			return
		}
		writeError(w, http.StatusInternalServerError, err)
		return
	}

	// HTTP 200 表示调用返回了执行结论；命令是否成功由 Status 表达。
	writeJSON(w, http.StatusOK, res)
}
