package sandboxclient_test

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/sandboxclient"
)

func probeCalls() map[string]func(*sandboxclient.Client) error {
	return map[string]func(*sandboxclient.Client) error{
		"/version": func(c *sandboxclient.Client) error { _, err := c.Version(context.Background()); return err },
		"/run": func(c *sandboxclient.Client) error {
			_, err := c.Probe(context.Background(), contract.RunSpec{Command: []string{"true"}})
			return err
		},
	}
}

func TestProbeRequiresCompleteBoundedJSON(t *testing.T) {
	const valid = `{"name":"cherry-oj-sandbox","version":"test","status":"OK"}`
	for path, call := range probeCalls() {
		for _, tc := range []struct {
			name, body string
			ok         bool
		}{
			{"valid", valid, true},
			{"below-limit", valid + strings.Repeat(" ", (16<<10)-1-len(valid)), true},
			{"at-limit", valid + strings.Repeat(" ", (16<<10)-len(valid)), true},
			{"above-limit", valid + strings.Repeat(" ", (16<<10)+1-len(valid)), false},
			{"second-object", valid + `{}`, false},
			{"trailing-garbage", valid + " invalid", false},
		} {
			t.Run(path+"/"+tc.name, func(t *testing.T) {
				srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					if r.URL.Path != path {
						t.Errorf("path = %s, want %s", r.URL.Path, path)
					}
					_, _ = io.WriteString(w, tc.body)
				}))
				defer srv.Close()
				err := call(sandboxclient.New(srv.URL, time.Second))
				if (err == nil) != tc.ok {
					t.Fatalf("accepted = %v, want %v: %v", err == nil, tc.ok, err)
				}
			})
		}
	}
}

func TestProbeRejectsRedirectsAndNonOKStatus(t *testing.T) {
	var targetHits atomic.Int32
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		targetHits.Add(1)
		_, _ = io.WriteString(w, `{"status":"OK"}`)
	}))
	defer target.Close()
	for path, call := range probeCalls() {
		for _, status := range []int{201, 204, 301, 302, 303, 307, 308, 500} {
			t.Run(fmt.Sprintf("%s/%d", path, status), func(t *testing.T) {
				srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					w.Header().Set("Location", target.URL+r.URL.Path)
					w.WriteHeader(status)
					_, _ = io.WriteString(w, `{"status":"OK"}`)
				}))
				defer srv.Close()
				if err := call(sandboxclient.New(srv.URL, time.Second)); err == nil {
					t.Fatalf("accepted HTTP %d", status)
				}
			})
		}
	}
	if targetHits.Load() != 0 {
		t.Fatalf("probe followed redirects: %d target requests", targetHits.Load())
	}
}

// 仅探测使用 16 KiB 上限；正常执行的合法输出不能被连带缩小。
func TestRunStillAllowsLargerOutput(t *testing.T) {
	stdout := strings.Repeat("x", 32<<10)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = fmt.Fprintf(w, `{"status":"OK","stdout":%q}`, stdout)
	}))
	defer srv.Close()
	result, err := sandboxclient.New(srv.URL, time.Second).Run(context.Background(), contract.RunSpec{})
	if err != nil || result.Stdout != stdout {
		t.Fatalf("normal run output truncated or rejected: %v, length %d", err, len(result.Stdout))
	}
}
