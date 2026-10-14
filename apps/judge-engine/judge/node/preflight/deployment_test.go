// 白盒测试注入受保护文件与 cgroup 的读取函数，不需要 root，也不改动宿主机 cgroup。
package preflight

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func deploymentFixture() deploymentManifest {
	m := deploymentManifest{Version: 1, Backend: "linux", Architecture: "amd64", Files: map[string]deploymentFile{}}
	for _, key := range []string{"executor", "rootfsManifest", "toolchainLock", "executorConfig", "startConfig", "slice", "judgeUnit", "bootstrap"} {
		m.Files[key] = deploymentFile{Path: "/release/" + key, SHA256: strings.Repeat("a", 64)}
	}
	return m
}

func TestDeploymentRejectsChangedFiles(t *testing.T) {
	for _, kind := range []string{"valid", "missing-file", "changed-file", "read-error", "unknown-field"} {
		t.Run(kind, func(t *testing.T) {
			m := deploymentFixture()
			hash := func(string) (string, error) { return strings.Repeat("a", 64), nil }
			switch kind {
			case "missing-file":
				delete(m.Files, "executor")
			case "changed-file":
				hash = func(string) (string, error) { return strings.Repeat("b", 64), nil }
			case "read-error":
				hash = func(string) (string, error) { return "", errors.New("unreadable") }
			case "unknown-field":
				m.Files["extra"] = deploymentFile{}
			}
			err := verifyManifest(context.Background(), m, hash)
			if (err == nil) != (kind == "valid") {
				t.Fatalf("err=%v", err)
			}
			// 报错必须指出是哪一项，否则出问题时只知道「部署不合格」，不知道去看哪里。
			named := map[string]string{
				"unknown-field": "extra",
				"missing-file":  "executor",
			}
			if want, ok := named[kind]; ok && !strings.Contains(err.Error(), want) {
				t.Fatalf("错误信息 %q 中没有指出是哪一项（期望含 %q）", err, want)
			}
		})
	}
}

func TestDeploymentRejectsUnprotectedPath(t *testing.T) {
	path := filepath.Join(t.TempDir(), "manifest.json")
	if err := os.WriteFile(path, []byte("{}"), 0666); err != nil {
		t.Fatal(err)
	}
	if err := os.Chmod(path, 0666); err != nil {
		t.Fatal(err)
	}
	f, err := openProtected(path)
	if err == nil {
		f.Close()
		t.Fatal("writable metadata accepted")
	}
}
