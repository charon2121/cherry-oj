package node_test

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/judge/node"
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
			if _, err := node.ProbeEnvironment(context.Background(), cfg); err == nil {
				t.Fatal("invalid deployment accepted")
			}
		})
	}
}
