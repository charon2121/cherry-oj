// White-box tests inject protected file and cgroup readers; no root privileges
// or host cgroup mutation is needed to test identity validation failures.
package probe

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
	for _, key := range []string{"sandbox", "helper", "rootfsManifest", "toolchainLock", "helperConfig", "sandboxConfig", "slice", "helperUnit", "sandboxUnit", "judgeUnit", "bootstrap"} {
		m.Files[key] = deploymentFile{Path: "/release/" + key, SHA256: strings.Repeat("a", 64)}
	}
	base := "/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice"
	for _, group := range []string{"", "/cherry-sandbox-helper.service", "/cherry-sandbox.service", "/cherry-sandbox-judge.service", "/cherry-sandbox-helper.service/supervisor", "/cherry-sandbox-helper.service/jobs"} {
		for name, value := range map[string]string{"cpu.max": "100000 100000", "memory.max": "1024", "memory.swap.max": "0", "pids.max": "64"} {
			m.Limits[base+group+"/"+name] = value
		}
	}
	return m
}

func TestDeploymentIdentityRejectsChangedFilesAndLimits(t *testing.T) {
	for _, kind := range []string{"valid", "missing-file", "changed-file", "read-error", "missing-limit", "unbounded", "swap", "changed-limit", "unknown-field", "unverified-limit"} {
		t.Run(kind, func(t *testing.T) {
			m := deploymentFixture()
			hash := func(string) (string, error) { return strings.Repeat("a", 64), nil }
			read := func(path string) ([]byte, error) { return []byte(m.Limits[path] + "\n"), nil }
			switch kind {
			case "missing-file":
				delete(m.Files, "helper")
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
			digest, err := verifyManifest(context.Background(), m, hash, read)
			if (err == nil) != (kind == "valid") {
				t.Fatalf("digest=%s err=%v", digest, err)
			}
			// 报错必须指出是哪一项，否则出问题时只知道「部署不合格」，不知道去看哪里。
			named := map[string]string{
				"unknown-field":    "extra",
				"unverified-limit": "/sys/fs/cgroup/elsewhere/pids.max",
				"missing-file":     "helper",
				"missing-limit":    "/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/pids.max",
			}
			if want, ok := named[kind]; ok && !strings.Contains(err.Error(), want) {
				t.Fatalf("错误信息 %q 中没有指出是哪一项（期望含 %q）", err, want)
			}
		})
	}
}

func TestDeploymentIdentityChangesWithRootfsAndPolicy(t *testing.T) {
	fingerprint := func(m deploymentManifest) string {
		t.Helper()
		hash := func(path string) (string, error) {
			for _, entry := range m.Files {
				if entry.Path == path {
					return entry.SHA256, nil
				}
			}
			return "", errors.New("unknown file")
		}
		digest, err := verifyManifest(context.Background(), m, hash, func(path string) ([]byte, error) { return []byte(m.Limits[path]), nil })
		if err != nil {
			t.Fatal(err)
		}
		return digest
	}
	original := fingerprint(deploymentFixture())
	for _, key := range []string{"rootfsManifest", "helper", "sandbox", "helperConfig", "slice", "bootstrap"} {
		m := deploymentFixture()
		entry := m.Files[key]
		entry.SHA256 = strings.Repeat("b", 64)
		m.Files[key] = entry
		if fingerprint(m) == original {
			t.Fatal("changed execution environment reused identity:", key)
		}
	}
}

func TestCPUIdentityIgnoresVolatileCounters(t *testing.T) {
	first, err := cpuIdentity("model name : Example\nflags : a b\ncpu MHz : 100\n")
	if err != nil {
		t.Fatal(err)
	}
	second, err := cpuIdentity("model name : Example\nflags : a b\ncpu MHz : 200\nmodel name : Example\n")
	if err != nil || first != second {
		t.Fatal("volatile data changed identity", err)
	}
	third, err := cpuIdentity("model name : Example\nflags : a c\n")
	if err != nil || first == third {
		t.Fatal("CPU feature change lost", err)
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

// 原生部署要求 sandbox 就是本机同批安装的那一个：跨主机的端点不受这份清单约束，
// 校验清单也就证明不了实际执行环境。端口由部署决定，不写死。
func TestNativeDeploymentRequiresLoopbackSandbox(t *testing.T) {
	for _, url := range []string{"http://127.0.0.1:15050", "http://127.0.0.1:5050", "http://[::1]:5050"} {
		if err := requireLoopback(url); err != nil {
			t.Errorf("回环端点被拒绝 %q: %v", url, err)
		}
	}
	for _, url := range []string{"http://10.0.0.4:5050", "http://sandbox:5050", "http://example.com"} {
		if err := requireLoopback(url); err == nil {
			t.Errorf("非回环端点被接受: %q", url)
		}
	}
}
