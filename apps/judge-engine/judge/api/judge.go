package api

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"

	"cherry-oj/judge-engine/internal/contract"
)

// maxJudgeRequestBytes 限制 /judge 请求体：完整源码加上 trial 模式内联的测试点。
// 不设上限时，一个超大请求会在解码时被整份读进内存。
const maxJudgeRequestBytes = 16 << 20

func (s *Server) handleJudge(w http.ResponseWriter, r *http.Request) {
	req, err := decodeJudgeRequest(http.MaxBytesReader(w, r.Body, maxJudgeRequestBytes))
	if err != nil {
		status := http.StatusBadRequest
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			status = http.StatusRequestEntityTooLarge
		}
		writeError(w, status, err)
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
	SubmissionID     *string            `json:"submissionId"`
	ProblemID        *string            `json:"problemId"`
	TestDataLocation *string            `json:"testDataLocation"`
	LanguageID       *string            `json:"languageId"`
	Source           *string            `json:"source"`
	Limits           *judgeLimitsJSON   `json:"limits"`
	Mode             contract.JudgeMode `json:"mode"`
	Testcases        []testcaseSpecJSON `json:"testcases"`
}

type judgeLimitsJSON struct {
	CPUNs       *int64 `json:"cpuNs"`
	MemoryBytes *int64 `json:"memoryBytes"`
	ClockNs     *int64 `json:"clockNs"`
}

type testcaseSpecJSON struct {
	Input    *string `json:"input"`
	Expected *string `json:"expected"`
	Name     string  `json:"name"`
}

func decodeJudgeRequest(body io.Reader) (contract.JudgeRequest, error) {
	var wire judgeRequestJSON
	decoder := json.NewDecoder(body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&wire); err != nil {
		return contract.JudgeRequest{}, fmt.Errorf("parse JudgeRequest: %w", err)
	}

	// 一个请求体只能有一个 JSON 值。否则 `{} {}` 会悄悄忽略后半段。
	var trailing json.RawMessage
	if err := decoder.Decode(&trailing); err != io.EOF {
		if err == nil {
			return contract.JudgeRequest{}, fmt.Errorf("extra JSON value after JudgeRequest")
		}
		return contract.JudgeRequest{}, fmt.Errorf("parse the tail after JudgeRequest: %w", err)
	}

	if wire.SubmissionID == nil {
		return contract.JudgeRequest{}, missing("submissionId")
	}
	if wire.ProblemID == nil {
		return contract.JudgeRequest{}, missing("problemId")
	}
	// 只有 trial 模式的测试点在请求里；缺省的 mode 是 submit，必须带测试数据的地址。
	if wire.TestDataLocation == nil && wire.Mode != contract.ModeTrial {
		return contract.JudgeRequest{}, missing("testDataLocation")
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

	testcases := make([]contract.TestcaseSpec, len(wire.Testcases))
	for i, c := range wire.Testcases {
		if c.Input == nil {
			return contract.JudgeRequest{}, missing(fmt.Sprintf("testcases[%d].input", i))
		}
		testcases[i] = contract.TestcaseSpec{Input: *c.Input, Name: c.Name}
		if c.Expected != nil {
			testcases[i].Expected = *c.Expected
		}
	}

	req := contract.JudgeRequest{
		SubmissionID: *wire.SubmissionID,
		ProblemID:    *wire.ProblemID,
		LanguageID:   *wire.LanguageID,
		Source:       *wire.Source,
		Limits:       limits,
		Mode:         wire.Mode,
		Testcases:    testcases,
	}
	if wire.TestDataLocation != nil {
		req.TestDataLocation = *wire.TestDataLocation
	}
	return req, nil
}

func missing(field string) error {
	return fmt.Errorf("missing required field %s", field)
}
