package api

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"

	"cherry-oj/judge-engine/internal/contract"
)

func (s *Server) handleJudge(w http.ResponseWriter, r *http.Request) {
	req, err := decodeJudgeRequest(r.Body)
	if err != nil {
		writeError(w, http.StatusBadRequest, err)
		return
	}

	// WA / TLE / CE / RE / SE 都是一次正常完成的判题结论。
	// HTTP 只描述这次对话是否成功，因此统一返回 200。
	result := s.judger.Judge(r.Context(), req)
	writeJSON(w, http.StatusOK, result)
}

// 这些 wire 类型用指针区分「字段没出现」与「字段出现但值是零值」。
// contract 类型保持纯数据结构，不为 HTTP 解码细节引入一层指针。
type judgeRequestJSON struct {
	SubmissionID      *string            `json:"submissionId"`
	ProblemID         *string            `json:"problemId"`
	ProblemVersionID  *string            `json:"problemVersionId"`
	TestDataVersionID *string            `json:"testDataVersionId"`
	LanguageID        *string            `json:"languageId"`
	Source            *string            `json:"source"`
	Limits            *judgeLimitsJSON   `json:"limits"`
	Mode              contract.JudgeMode `json:"mode"`
	Cases             []caseSpecJSON     `json:"cases"`
}

type judgeLimitsJSON struct {
	CPUNs       *int64 `json:"cpuNs"`
	MemoryBytes *int64 `json:"memoryBytes"`
	ClockNs     *int64 `json:"clockNs"`
}

type caseSpecJSON struct {
	Input    *string `json:"input"`
	Expected *string `json:"expected"`
	Name     string  `json:"name"`
}

func decodeJudgeRequest(body io.Reader) (contract.JudgeRequest, error) {
	var wire judgeRequestJSON
	decoder := json.NewDecoder(body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&wire); err != nil {
		return contract.JudgeRequest{}, fmt.Errorf("解析 JudgeRequest: %w", err)
	}

	// 一个请求体只能有一个 JSON 值。否则 `{} {}` 会悄悄忽略后半段。
	var trailing json.RawMessage
	if err := decoder.Decode(&trailing); err != io.EOF {
		if err == nil {
			return contract.JudgeRequest{}, fmt.Errorf("JudgeRequest 后还有多余的 JSON 值")
		}
		return contract.JudgeRequest{}, fmt.Errorf("解析 JudgeRequest 尾部: %w", err)
	}

	if wire.SubmissionID == nil {
		return contract.JudgeRequest{}, missing("submissionId")
	}
	if wire.ProblemID == nil {
		return contract.JudgeRequest{}, missing("problemId")
	}
	if wire.ProblemVersionID == nil {
		return contract.JudgeRequest{}, missing("problemVersionId")
	}
	if wire.TestDataVersionID == nil {
		return contract.JudgeRequest{}, missing("testDataVersionId")
	}
	if wire.LanguageID == nil {
		return contract.JudgeRequest{}, missing("languageId")
	}
	if wire.Source == nil {
		return contract.JudgeRequest{}, missing("source")
	}
	if wire.Limits == nil {
		return contract.JudgeRequest{}, missing("limits")
	}
	if wire.Limits.CPUNs == nil {
		return contract.JudgeRequest{}, missing("limits.cpuNs")
	}
	if wire.Limits.MemoryBytes == nil {
		return contract.JudgeRequest{}, missing("limits.memoryBytes")
	}

	limits := contract.JudgeLimits{
		CPUNs:       *wire.Limits.CPUNs,
		MemoryBytes: *wire.Limits.MemoryBytes,
	}
	if wire.Limits.ClockNs != nil {
		limits.ClockNs = *wire.Limits.ClockNs
	}

	cases := make([]contract.CaseSpec, len(wire.Cases))
	for i, c := range wire.Cases {
		if c.Input == nil {
			return contract.JudgeRequest{}, missing(fmt.Sprintf("cases[%d].input", i))
		}
		cases[i] = contract.CaseSpec{Input: *c.Input, Name: c.Name}
		if c.Expected != nil {
			cases[i].Expected = *c.Expected
		}
	}

	return contract.JudgeRequest{
		SubmissionID:      *wire.SubmissionID,
		ProblemID:         *wire.ProblemID,
		ProblemVersionID:  *wire.ProblemVersionID,
		TestDataVersionID: *wire.TestDataVersionID,
		LanguageID:        *wire.LanguageID,
		Source:            *wire.Source,
		Limits:            limits,
		Mode:              wire.Mode,
		Cases:             cases,
	}, nil
}

func missing(field string) error {
	return fmt.Errorf("缺少必填字段 %s", field)
}
