package probe_test

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"cherry-oj/judge-engine/judge/internal/config"
	"cherry-oj/judge-engine/judge/internal/node/probe"
	"cherry-oj/judge-engine/judge/internal/sandboxclient"
)

func TestEnvironmentRejectsUntrustedProbeTransport(t *testing.T) {
	for _, kind := range []string{"redirect", "oversize-run", "trailing-version"} {
		t.Run(kind, func(t *testing.T) {
			var hits atomic.Int32
			target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				hits.Add(1)
				if r.URL.Path == "/version" {
					fmt.Fprint(w, `{"name":"cherry-oj-sandbox","version":"test","isolation":"devhost"}`)
					if kind == "trailing-version" {
						fmt.Fprint(w, ` garbage`)
					}
					return
				}
				metadata := `{"Architecture":"amd64","CPUModel":"test","OSVersion":"test","KernelVersion":"test","ToolchainVersion":"test","RuntimeDigest":"test"}`
				stderr := ""
				if kind == "oversize-run" {
					stderr = strings.Repeat("x", 1<<20)
				}
				_ = json.NewEncoder(w).Encode(map[string]any{"status": "OK", "exitCode": 0, "stdout": metadata, "stderr": stderr})
			}))
			defer target.Close()
			redirect := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				http.Redirect(w, r, target.URL+r.URL.Path, http.StatusTemporaryRedirect)
			}))
			defer redirect.Close()
			cfg := config.Default().Judge
			cfg.SandboxURL = target.URL
			if kind == "redirect" {
				cfg.SandboxURL = redirect.URL
			}
			ctx, cancel := context.WithTimeout(context.Background(), time.Second)
			defer cancel()
			_, err := probe.Environment(ctx, cfg, sandboxclient.New(cfg.SandboxURL, time.Second))
			t.Logf("scenario=%s accepted=%v targetRequests=%d err=%v", kind, err == nil, hits.Load(), err)
			if err == nil {
				t.Errorf("probe accepted %s", kind)
			}
			if kind == "redirect" && hits.Load() != 0 {
				t.Error("probe followed redirect")
			}
		})
	}
}
