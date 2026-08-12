package api

import (
	"cherry-oj/judge-engine/internal/contract"
	"encoding/json"
	"errors"
	"net/http"
)

func (s *Server) handleRun(w http.ResponseWriter, r *http.Request) {
	var spec contract.RunSpec
	if err := json.NewDecoder(r.Body).Decode(&spec); err != nil {
		writeError(w, http.StatusBadRequest, err) // 400：JSON 解析错误
		return
	}

	if len(spec.Command) == 0 { // 400：空 Command 不能运行
		writeError(w, http.StatusBadRequest, errors.New("command 不能为空"))
		return
	}

	res, err := s.exec.Run(r.Context(), spec)
	if err != nil {
		// 目前只有「排队时被取消」会走到这
		writeError(w, http.StatusInternalServerError, err)
		return
	}

	writeJSON(w, http.StatusOK, res) // 直接就是 RunResult，不套 results[]
}
