package preflight_test

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/node/preflight"
	"cherry-oj/judge-engine/judge/sandboxclient"
)

func TestCheckRefusesMissingManifestOrWrongBackend(t *testing.T) {
	for _, backend := range []string{"linux", "host"} {
		t.Run(backend, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.URL.Path != "/version" {
					t.Error("execution reached before deployment validation")
				}
				fmt.Fprintf(w, `{"name":"cherry-oj-sandbox","version":"0.1.0-mvp","isolation":%q}`, backend)
			}))
			defer server.Close()
			cfg := config.Default().Judge
			cfg.SandboxURL = server.URL
			if backend == "host" {
				cfg.Node.DeploymentManifest = "/etc/cherry-sandbox/deployment.json"
			}
			client := sandboxclient.New(server.URL, 5*time.Second)
			if err := preflight.Check(context.Background(), cfg, client); err == nil {
				t.Fatal("invalid deployment accepted")
			}
		})
	}
}

// 自检消费的是 judge 已有的 sandbox 客户端能力，不自建另一个 HTTP 客户端。
var _ preflight.Sandbox = (*sandboxclient.Client)(nil)
