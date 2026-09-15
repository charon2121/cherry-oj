// 白盒测试用于替换 HTTP transport，并用 synctest 控制注册退避与心跳时钟。
package node

import (
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/config"
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
	n, err := New(c, Environment{}, slog.New(slog.NewTextHandler(io.Discard, nil)))
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
	n, err := New(c, Environment{}, nil)
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
	env := Environment{Architecture: "amd64", CPUModel: "base CPU", OSVersion: "base os",
		KernelVersion: "base kernel", SandboxVersion: "base sandbox",
		ToolchainVersion: "base compiler", RuntimeDigest: "base quotas"}
	fingerprint := func(c config.Settings, env Environment) string {
		t.Helper()
		c.TestdataRoot = t.TempDir()
		n, e := New(c, env, nil)
		if e != nil {
			t.Fatal(e)
		}
		defer n.Close()
		return n.Registration().EnvironmentFingerprint
	}
	original := fingerprint(c, env)

	// 节点位置属于「这台机器是谁」，不属于「这是什么环境」：同一环境的两个节点必须同指纹。
	c.Node.ID = "another-node"
	c.Node.AdvertiseURL = "http://127.0.0.1:9999"
	if fingerprint(c, env) != original {
		t.Fatal("identity changed compatibility group")
	}

	// 执行环境的事实变了，指纹必须变。
	for name, change := range map[string]func(*Environment){
		"CPU":     func(e *Environment) { e.CPUModel = "other CPU" },
		"内核":      func(e *Environment) { e.KernelVersion = "other kernel" },
		"工具链":     func(e *Environment) { e.ToolchainVersion = "other compiler" },
		"运行时配额":   func(e *Environment) { e.RuntimeDigest = "other quotas" },
		"架构":      func(e *Environment) { e.Architecture = "arm64" },
		"sandbox": func(e *Environment) { e.SandboxVersion = "other sandbox" },
	} {
		changed := env
		change(&changed)
		if fingerprint(c, changed) == original {
			t.Fatalf("%s 变化后仍复用了指纹", name)
		}
	}

	// 判题策略变了，指纹同样必须变——同一份提交可能因此得到不同结论。
	strict := c
	strict.StrictWhitespace = !strict.StrictWhitespace
	if fingerprint(strict, env) == original {
		t.Fatal("空白严格度变化后仍复用了指纹")
	}
}
