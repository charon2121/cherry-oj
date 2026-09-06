// 白盒测试用于替换 HTTP transport，并用 synctest 控制注册退避与心跳时钟。
package node

import (
	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
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
)

type transportFunc func(*http.Request) (*http.Response, error)

func (f transportFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }
func TestRunRetriesRegistersHeartbeatsAndStops(t *testing.T) {
	c := config.Default().Judge
	c.Node.Enabled = true
	c.Node.ControlToken = "control"
	c.TestdataRoot = t.TempDir()
	n, err := New(c, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatal(err)
	}
	defer n.Close()
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
	c := config.Default().Judge
	c.Node.Enabled, c.Node.ControlToken, c.TestdataRoot = true, "control", t.TempDir()
	n, err := New(c, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer n.Close()
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

func TestEnvironmentFingerprintChangesWithRuntimeButNotNodeIdentity(t *testing.T) {
	c := config.Default().Judge
	c.Node.Enabled, c.Node.ControlToken = true, "control"
	fingerprint := func(c config.JudgeConfig) string {
		t.Helper()
		c.TestdataRoot = t.TempDir()
		n, e := New(c, nil)
		if e != nil {
			t.Fatal(e)
		}
		defer n.Close()
		return n.Registration().EnvironmentFingerprint
	}
	original := fingerprint(c)
	c.Node.ID = "another-node"
	c.Node.AdvertiseURL = "http://127.0.0.1:9999"
	if fingerprint(c) != original {
		t.Fatal("identity changed compatibility group")
	}
	for _, change := range []func(*config.JudgeConfig){func(c *config.JudgeConfig) { c.Node.CPUModel = "other CPU" }, func(c *config.JudgeConfig) { c.Node.KernelVersion = "other kernel" }, func(c *config.JudgeConfig) { c.Node.ToolchainVersion = "other compiler" }, func(c *config.JudgeConfig) { c.Node.RuntimeDigest = "other quotas" }, func(c *config.JudgeConfig) { c.StrictWhitespace = !c.StrictWhitespace }} {
		changed := c
		change(&changed)
		if fingerprint(changed) == original {
			t.Fatal("runtime change reused fingerprint")
		}
	}
}
