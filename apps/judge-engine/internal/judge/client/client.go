package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/tracecontext"
)

const (
	defaultTimeout    = 60 * time.Second
	errorBodyMaxBytes = 4 << 10
)

// Client 是 sandbox 的 HTTP 客户端。
//
// 它只判断一次 HTTP 对话是否成功，不解释被执行程序的状态：sandbox 正常返回
// TimeLimitExceeded / NonzeroExitStatus 时，Run 的 error 仍然是 nil。
type Client struct {
	baseURL string
	http    *http.Client
}

// New 创建一个 sandbox 客户端。
// timeout 必须覆盖 sandbox 最慢的一次操作；非正值使用安全默认值，避免零值客户端永不超时。
func New(baseURL string, timeout time.Duration) *Client {
	if timeout <= 0 {
		timeout = defaultTimeout
	}
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"),
		http: &http.Client{
			Timeout:   timeout,
			Transport: tracecontext.Transport(nil),
		},
	}
}

// Upload 把一袋字节流式存进 sandbox 的 store，返回后续 /run 可引用的 ref。
func (c *Client) Upload(ctx context.Context, body io.Reader) (string, error) {
	if body == nil {
		return "", fmt.Errorf("upload blob: body is nil")
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.endpoint("/blobs"), body)
	if err != nil {
		return "", fmt.Errorf("upload blob: create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/octet-stream")

	resp, err := c.http.Do(req)
	if err != nil {
		return "", fmt.Errorf("upload blob: %w", err)
	}
	defer drainAndClose(resp.Body)

	if !isSuccess(resp.StatusCode) {
		return "", responseError("upload blob", resp)
	}

	var result struct {
		Ref string `json:"ref"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", fmt.Errorf("upload blob: decode response: %w", err)
	}
	if result.Ref == "" {
		return "", fmt.Errorf("upload blob: response ref is empty")
	}
	return result.Ref, nil
}

// Run 请求 sandbox 执行一条命令。
// RunResult.Status 不是 OK 仍是一次成功的 HTTP 对话，不会被转换成 error。
func (c *Client) Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error) {
	body, err := json.Marshal(spec)
	if err != nil {
		return contract.RunResult{}, fmt.Errorf("run sandbox command: encode request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.endpoint("/run"), bytes.NewReader(body))
	if err != nil {
		return contract.RunResult{}, fmt.Errorf("run sandbox command: create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.http.Do(req)
	if err != nil {
		return contract.RunResult{}, fmt.Errorf("run sandbox command: %w", err)
	}
	defer drainAndClose(resp.Body)

	if !isSuccess(resp.StatusCode) {
		return contract.RunResult{}, responseError("run sandbox command", resp)
	}

	var result contract.RunResult
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return contract.RunResult{}, fmt.Errorf("run sandbox command: decode response: %w", err)
	}
	if result.Status == "" {
		return contract.RunResult{}, fmt.Errorf("run sandbox command: response status is empty")
	}
	return result, nil
}

// Delete 删除一个 store ref。删除不存在的 ref 视为成功，便于清理路径安全重试。
func (c *Client) Delete(ctx context.Context, ref string) error {
	if ref == "" {
		return fmt.Errorf("delete blob: ref is empty")
	}

	path := "/blobs/" + url.PathEscape(ref)
	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, c.endpoint(path), nil)
	if err != nil {
		return fmt.Errorf("delete blob %q: create request: %w", ref, err)
	}

	resp, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("delete blob %q: %w", ref, err)
	}
	defer drainAndClose(resp.Body)

	if resp.StatusCode == http.StatusNotFound || isSuccess(resp.StatusCode) {
		return nil
	}
	return responseError(fmt.Sprintf("delete blob %q", ref), resp)
}

func (c *Client) endpoint(path string) string {
	return c.baseURL + path
}

func isSuccess(status int) bool {
	return status >= http.StatusOK && status < http.StatusMultipleChoices
}

// responseError 保留 sandbox 返回的正文片段。真正的原因通常就在 JSON error 字段里，
// 但不能无上限读错误响应，否则一个异常对端可以拖垮 judge。
func responseError(operation string, resp *http.Response) error {
	body, err := io.ReadAll(io.LimitReader(resp.Body, errorBodyMaxBytes+1))
	if err != nil {
		return fmt.Errorf("%s: sandbox returned %s; read response body: %w", operation, resp.Status, err)
	}

	truncated := len(body) > errorBodyMaxBytes
	if truncated {
		body = body[:errorBodyMaxBytes]
	}
	message := strings.TrimSpace(string(body))
	if message == "" {
		message = "<empty body>"
	}
	if truncated {
		message += "..."
	}
	return fmt.Errorf("%s: sandbox returned %s: %s", operation, resp.Status, message)
}

func drainAndClose(body io.ReadCloser) {
	_, _ = io.Copy(io.Discard, body)
	_ = body.Close()
}
