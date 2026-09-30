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
	m := deploymentManifest{Version: 1, Backend: "linux", Architecture: "amd64", Files: map[string]deploymentFile{}, Limits: map[string]string{}}
	for _, key := range []string{"executor", "rootfsManifest", "toolchainLock", "executorConfig", "startConfig", "slice", "judgeUnit", "bootstrap"} {
		m.Files[key] = deploymentFile{Path: "/release/" + key, SHA256: strings.Repeat("a", 64)}
	}
	base := "/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice"
	for _, group := range []string{"", "/cherry-sandbox-judge.service", "/cherry-sandbox-judge.service/supervisor", "/cherry-sandbox-judge.service/jobs"} {
		for name, value := range map[string]string{"cpu.max": "100000 100000", "memory.max": "1024", "memory.swap.max": "0", "pids.max": "64"} {
			m.Limits[base+group+"/"+name] = value
		}
	}
	return m
}

func TestDeploymentRejectsChangedFilesAndLimits(t *testing.T) {
	for _, kind := range []string{"valid", "missing-file", "changed-file", "read-error", "missing-limit", "unbounded", "swap", "changed-limit", "unknown-field", "unverified-limit"} {
		t.Run(kind, func(t *testing.T) {
			m := deploymentFixture()
			hash := func(string) (string, error) { return strings.Repeat("a", 64), nil }
			read := func(path string) ([]byte, error) { return []byte(m.Limits[path] + "\n"), nil }
			switch kind {
			case "missing-file":
				delete(m.Files, "executor")
			case "changed-file":
				hash = func(string) (string, error) { return strings.Repeat("b", 64), nil }
			case "read-error":
				hash = func(string) (string, error) { return "", errors.New("unreadable") }
			case "missing-limit":
				delete(m.Limits, "/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/pids.max")
			case "unbounded":
				m.Limits["/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/pids.max"] = "max"
			case "swap":
				m.Limits["/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/memory.swap.max"] = "1"
			case "changed-limit":
				read = func(string) ([]byte, error) { return []byte("unexpected"), nil }
			case "unknown-field":
				m.Files["extra"] = deploymentFile{}
			case "unverified-limit":
				// 清单声明了一条从未被核对的上界。只比数量的话它会和「少一条」互相抵消。
				m.Limits["/sys/fs/cgroup/elsewhere/pids.max"] = "1"
			}
			err := verifyManifest(context.Background(), m, hash, read)
			if (err == nil) != (kind == "valid") {
				t.Fatalf("err=%v", err)
			}
			// 报错必须指出是哪一项，否则出问题时只知道「部署不合格」，不知道去看哪里。
			named := map[string]string{
				"unknown-field":    "extra",
				"unverified-limit": "/sys/fs/cgroup/elsewhere/pids.max",
				"missing-file":     "executor",
				"missing-limit":    "/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/pids.max",
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
