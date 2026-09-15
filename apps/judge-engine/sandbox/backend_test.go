package sandbox

import (
	"runtime"
	"strings"
	"testing"

	"cherry-oj/judge-engine/sandbox/internal/backend"
)

func TestBackendSelectionNeverFallsBack(t *testing.T) {
	c := DefaultConfig().Sandbox
	c.Backend = "unknown"
	if _, _, e := selectBackend(c); e == nil {
		t.Fatal("unknown accepted")
	}
	if runtime.GOOS != "linux" || runtime.GOARCH != "amd64" {
		c.Backend = backend.NameLinux
		if _, _, e := selectBackend(c); e == nil {
			t.Fatal("linux silently fell back")
		}
	}
}

// 零隔离后端必须显式承认才能启用：误用它跑用户提交等于没有沙箱。
func TestDevHostRequiresExplicitAcknowledgement(t *testing.T) {
	c := DefaultConfig().Sandbox
	c.Backend = backend.NameDevHost

	if _, _, e := selectBackend(c); e == nil {
		t.Fatal("零隔离后端在未显式承认时被启用")
	} else if !strings.Contains(e.Error(), "allowUnsafeBackend") {
		t.Fatalf("错误信息没有指出怎么开启: %v", e)
	}

	cfg := DefaultConfig()
	cfg.Sandbox.Backend = backend.NameDevHost
	if e := cfg.Validate(); e == nil {
		t.Fatal("配置校验没有挡住未承认的零隔离后端")
	}
	cfg.Sandbox.AllowUnsafeBackend = true
	if e := cfg.Validate(); e != nil {
		t.Fatalf("显式承认后仍被拒绝: %v", e)
	}

	c.AllowUnsafeBackend = true
	b, closeFn, e := selectBackend(c)
	if e != nil || b == nil {
		t.Fatalf("显式承认后仍无法装配: %v", e)
	}
	if e := closeFn(); e != nil {
		t.Fatal(e)
	}
}
