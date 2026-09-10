package api

import (
	"cherry-oj/judge-engine/internal/contract"

	"encoding/json"
	"errors"
	"io"
	"net/http"
)

func (s *Server) handleRun(w http.ResponseWriter, r *http.Request) {
	var spec contract.RunSpec
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, s.opts.MaxRequestBytes))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&spec); err != nil {
		writeError(w, http.StatusBadRequest, err) // 400：JSON 解析错误
		return
	}

	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		writeError(w, http.StatusBadRequest, errors.New("JSON有尾随内容"))
		return
	}
	if err := spec.Limits.Validate(); err != nil {
		writeError(w, http.StatusBadRequest, err)
		return
	}
	if len(spec.Command) == 0 { // 400：空 Command 不能运行
		writeError(w, http.StatusBadRequest, errors.New("command 不能为空"))
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

	writeJSON(w, http.StatusOK, res) // 直接就是 RunResult，不套 results[]
}
