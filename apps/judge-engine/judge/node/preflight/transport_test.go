package preflight_test

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/node/preflight"
	"cherry-oj/judge-engine/judge/sandboxclient"
)

// 自检确认的是「本机配置的那个 sandbox」：跟随重定向或容忍多余字节，都可能把别的地址当成它。
func TestCheckRejectsUntrustedSandboxTransport(t *testing.T) {
	for _, kind := range []string{"trusted", "redirect", "trailing-version"} {
		t.Run(kind, func(t *testing.T) {
			var hits atomic.Int32
			target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				hits.Add(1)
				fmt.Fprint(w, `{"name":"cherry-oj-sandbox","version":"test","isolation":"devhost"}`)
				if kind == "trailing-version" {
					fmt.Fprint(w, ` garbage`)
				}
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
			err := preflight.Check(ctx, cfg, sandboxclient.New(cfg.SandboxURL, time.Second))
			if (err == nil) != (kind == "trusted") {
				t.Fatalf("scenario=%s err=%v", kind, err)
			}
			if kind == "redirect" && hits.Load() != 0 {
				t.Error("preflight followed redirect")
			}
		})
	}
}
