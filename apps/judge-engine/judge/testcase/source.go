package testcase

import (
	"context"
	"fmt"
	"io"
	"io/fs"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// source 是测试数据目录的一种读法：本地目录，或一个 HTTP 基地址。
type source interface {
	// open 打开目录下的一个文件。文件不存在时返回的错误满足 errors.Is(err, fs.ErrNotExist)。
	open(ctx context.Context, name string) (io.ReadCloser, error)
}

// newSource 按协议解析地址：以 http:// 或 https:// 开头是 HTTP，以 / 开头是本地绝对路径，其余一律拒绝。
// 相对路径没有意义——judge 的工作目录不由写入方决定。
func newSource(location string, timeout time.Duration) (source, error) {
	switch {
	case strings.HasPrefix(location, "http://"), strings.HasPrefix(location, "https://"):
		return newHTTPSource(location, timeout)
	case strings.HasPrefix(location, "/"):
		return localSource{dir: filepath.Clean(location)}, nil
	default:
		return nil, fmt.Errorf("unsupported test data location %q: want an absolute path or an http(s) URL", location)
	}
}

type localSource struct{ dir string }

func (s localSource) open(_ context.Context, name string) (io.ReadCloser, error) {
	f, err := os.Open(filepath.Join(s.dir, name))
	if err != nil {
		return nil, err
	}
	// 管道之类的特殊文件会让读取永远阻塞。
	info, err := f.Stat()
	if err != nil {
		f.Close()
		return nil, err
	}
	if !info.Mode().IsRegular() {
		f.Close()
		return nil, fmt.Errorf("%s is not a regular file", f.Name())
	}
	return f, nil
}

type httpSource struct {
	base   string // 以 / 结尾
	client *http.Client
}

func newHTTPSource(location string, timeout time.Duration) (httpSource, error) {
	u, err := url.Parse(location)
	if err != nil {
		return httpSource{}, fmt.Errorf("parse test data location: %w", err)
	}
	// 地址会出现在错误信息和日志里，不允许携带凭据；查询串会破坏相对名字的拼接。
	if u.Host == "" || u.User != nil || u.RawQuery != "" || u.Fragment != "" {
		return httpSource{}, fmt.Errorf("unsupported test data location %q: want an http(s) URL with a host and no credentials, query or fragment", u.Redacted())
	}
	return httpSource{
		base:   strings.TrimRight(location, "/") + "/",
		client: &http.Client{Timeout: timeout},
	}, nil
}

func (s httpSource) open(ctx context.Context, name string) (io.ReadCloser, error) {
	target := s.base + name
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return nil, fmt.Errorf("GET %s: %w", target, err)
	}
	resp, err := s.client.Do(req)
	if err != nil {
		return nil, err
	}
	switch resp.StatusCode {
	case http.StatusOK:
		return resp.Body, nil
	case http.StatusNotFound:
		resp.Body.Close()
		return nil, fmt.Errorf("GET %s: %w", target, fs.ErrNotExist)
	default:
		resp.Body.Close()
		return nil, fmt.Errorf("GET %s: unexpected status %d", target, resp.StatusCode)
	}
}
