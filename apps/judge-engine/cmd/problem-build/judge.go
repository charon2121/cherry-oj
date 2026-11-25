package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"

	"cherry-oj/judge-engine/internal/contract"
)

const (
	maxRequestBytes  = 16 << 20 // /judge 的请求体上限
	maxResponseBytes = 16 << 20
)

type judgeClient struct {
	url    string
	client *http.Client
}

func newJudgeClient(url string) judgeClient {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.Proxy = nil
	return judgeClient{
		url: strings.TrimRight(url, "/") + "/judge",
		client: &http.Client{
			Transport: transport,
			Timeout:   buildTimeout,
			CheckRedirect: func(*http.Request, []*http.Request) error {
				return http.ErrUseLastResponse
			},
		},
	}
}

func (j judgeClient) trial(ctx context.Context, languageID string, limits contract.JudgeLimits, program program, specs []contract.TestcaseSpec) (contract.JudgeResult, error) {
	var result contract.JudgeResult
	var solution string
	if program.solution != nil {
		solution = program.solution.text
	}
	body, err := json.Marshal(contract.JudgeRequest{
		SubmissionID:   "problem-build-" + rand.Text(),
		ProblemID:      "problem-build",
		Mode:           contract.ModeTrial,
		LanguageID:     languageID,
		Source:         program.source.text,
		SolutionSource: solution,
		Limits:         limits,
		Testcases:      specs,
	})
	if err != nil {
		return result, err
	}
	if len(body) > maxRequestBytes {
		return result, fmt.Errorf("试运行请求超过 %d 字节", maxRequestBytes)
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, j.url, bytes.NewReader(body))
	if err != nil {
		return result, err
	}
	request.Header.Set("Content-Type", "application/json")
	response, err := j.client.Do(request)
	if err != nil {
		return result, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return result, fmt.Errorf("/judge 返回 HTTP %d", response.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(response.Body, maxResponseBytes+1))
	if err != nil {
		return result, err
	}
	if len(data) > maxResponseBytes {
		return result, fmt.Errorf("试运行响应超过 %d 字节", maxResponseBytes)
	}
	if err := json.Unmarshal(data, &result); err != nil {
		return result, fmt.Errorf("解析试运行响应：%w", err)
	}
	if !result.Verdict.IsValid() {
		return result, fmt.Errorf("收到未知判题结论 %q", result.Verdict)
	}
	if len(result.TestcaseResults) == 0 && (result.Verdict == contract.VerdictCE || result.Verdict == contract.VerdictSE) {
		return result, fmt.Errorf("试运行在逐点执行前返回 %s，详情见报告", result.Verdict)
	}
	if len(result.TestcaseResults) != len(specs) {
		return result, fmt.Errorf("判题返回 %d 个测试点，期望 %d 个", len(result.TestcaseResults), len(specs))
	}
	for i, row := range result.TestcaseResults {
		if row.Idx != i+1 || row.Name != specs[i].Name || !row.Verdict.IsValid() {
			return result, fmt.Errorf("测试点 %q 收到不一致的判题结果：idx=%d name=%q verdict=%q", specs[i].Name, row.Idx, row.Name, row.Verdict)
		}
	}
	return result, nil
}
