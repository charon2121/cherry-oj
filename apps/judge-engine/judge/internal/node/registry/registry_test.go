// 白盒注入传输层，避免为心跳用例起一个真实的控制面。
package registry

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"sync/atomic"
	"testing"
	"testing/synctest"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/config"
)

type transportFunc func(*http.Request) (*http.Response, error)

func (f transportFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

func testRegistry(t *testing.T) *Registry {
	t.Helper()
	c := config.Default().Judge
	c.Node.Enabled, c.Node.ControlToken = true, "control"
	// 会话号必须是合法 UUID：租约校验会拿它比对，随手写个字符串会让租约被判无效，
	// 于是节点一直重新注册而不是转入心跳。
	return New(c.Node, contract.NodeRegistration{NodeID: c.Node.ID,
		SessionID: "019c8e42-7f70-7000-8000-000000000001"},
		slog.New(slog.NewTextHandler(io.Discard, nil)))
}

func TestRunRetriesRegistersHeartbeatsAndStops(t *testing.T) {
	n := testRegistry(t)
	synctest.Test(t, func(t *testing.T) {
		var calls atomic.Int32
		n.client = &http.Client{Transport: transportFunc(func(r *http.Request) (*http.Response, error) {
			count := calls.Add(1)
			if r.Header.Get("Authorization") != "Bearer control" {
				t.Error("missing auth")
			}
			if count == 1 {
				return &http.Response{StatusCode: 503, Body: io.NopCloser(strings.NewReader("private failure"))}, nil
			}
			if count > 2 && !strings.HasSuffix(r.URL.Path, "/heartbeat") {
				t.Error("expected heartbeat")
			}
			b, e := json.Marshal(contract.NodeLease{NodeID: n.registration.NodeID, EnvironmentID: n.registration.SessionID, LeaseDurationNs: 35_000_000_000})
			if e != nil {
				t.Fatal(e)
			}
			return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader(string(b)))}, nil
		})}
		ctx, cancel := context.WithCancel(context.Background())
		done := make(chan struct{})
		go func() { defer close(done); n.Run(ctx) }()
		time.Sleep(12 * time.Second)
		synctest.Wait()
		cancel()
		<-done
		if calls.Load() != 3 {
			t.Fatalf("calls=%d", calls.Load())
		}
	})
}

func TestOldSessionConflictStopsRegistration(t *testing.T) {
	n := testRegistry(t)
	var calls atomic.Int32
	n.client = &http.Client{Transport: transportFunc(func(r *http.Request) (*http.Response, error) {
		calls.Add(1)
		return &http.Response{StatusCode: 409, Body: io.NopCloser(strings.NewReader("conflict"))}, nil
	})}
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	n.Run(ctx)
	if calls.Load() != 1 || ctx.Err() != nil {
		t.Fatal("old session kept retrying")
	}
}
