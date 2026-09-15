package probe_test

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/config"
	"cherry-oj/judge-engine/judge/internal/node/probe"
	"cherry-oj/judge-engine/judge/internal/sandboxclient"
)

func TestLinuxProbeRefusesMissingManifestOrWrongBackend(t *testing.T) {
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
			if _, err := probe.Environment(context.Background(), cfg, client); err == nil {
				t.Fatal("invalid deployment accepted")
			}
		})
	}
}

// 探测消费的是 judge 已有的 sandbox 客户端能力，不再自建第三个 HTTP 客户端。
var _ probe.Sandbox = (*sandboxclient.Client)(nil)

// 未配置部署清单且后端不是 linux 时，探测走通用路径；这里只确认接口契约成立。
var _ = contract.SandboxVersion{}
