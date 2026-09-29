package preflight

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"net/url"
	"os"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"syscall"

	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/node/wire"
)

// root 管理的部署清单：绑定不可变的发布文件与实际生效的 cgroup 上界。
// 它是本机部署元数据，不是对外协议，也不上报控制面。
type deploymentManifest struct {
	Version      int                       `json:"version"`
	Backend      string                    `json:"backend"`
	Architecture string                    `json:"architecture"`
	Files        map[string]deploymentFile `json:"files"`
	Limits       map[string]string         `json:"limits"`
}
type deploymentFile struct {
	Path   string `json:"path"`
	SHA256 string `json:"sha256"`
}

func checkDeployment(ctx context.Context, j config.Settings) error {
	// 原生部署下 sandbox 必须是本机同批安装的那一个：跨主机的端点不受这份部署清单约束，
	// 校验清单就证明不了实际执行的是哪一份。这里只要求回环地址，具体端口由部署配置决定。
	if err := requireLoopback(j.SandboxURL); err != nil {
		return err
	}
	if runtime.GOOS != "linux" || runtime.GOARCH != "amd64" {
		return fmt.Errorf("native deployment requires Linux/amd64")
	}
	return verifyDeployment(ctx, j.Node.DeploymentManifest)
}

func verifyDeployment(ctx context.Context, path string) error {
	file, err := openProtected(path)
	if err != nil {
		return err
	}
	defer file.Close()
	var manifest deploymentManifest
	if err = wire.Decode(io.LimitReader(file, 65537), &manifest); err != nil {
		return fmt.Errorf("deployment manifest: %w", err)
	}
	// 服务实际启动的是 releaseBinDir 下的符号链接；它指向的文件必须就是清单声明的那一个，
	// 否则校验的是清单里那份、跑的是另一份。
	for key, name := range map[string]string{"sandbox": "sandbox", "isolator": "isolator"} {
		active, err := filepath.EvalSymlinks(filepath.Join(releaseBinDir, name))
		if err != nil || active != manifest.Files[key].Path {
			return fmt.Errorf("active release differs from deployment file %s", key)
		}
	}
	return verifyManifest(ctx, manifest, protectedDigest, os.ReadFile)
}

// requireLoopback 只接受回环主机名，端口与方案仍由部署配置决定。
func requireLoopback(raw string) error {
	u, err := url.Parse(raw)
	if err != nil {
		return fmt.Errorf("native deployment requires a parsable sandbox endpoint: %w", err)
	}
	host := u.Hostname()
	if ip := net.ParseIP(host); ip == nil || !ip.IsLoopback() {
		return fmt.Errorf("native deployment requires a loopback sandbox endpoint, got %q", host)
	}
	return nil
}

func verifyManifest(ctx context.Context, manifest deploymentManifest, fileDigest func(string) (string, error), readLimit func(string) ([]byte, error)) error {
	if manifest.Version != 1 || manifest.Backend != "linux" || manifest.Architecture != "amd64" {
		return fmt.Errorf("unsupported deployment manifest")
	}
	// 双向覆盖：要求的每一项都必须在清单里，清单里的每一项也都必须是要求的。
	// 只比数量的话，多一项少一项会互相抵消，而报错也说不出是哪一项。
	for key := range manifest.Files {
		if !slices.Contains(requiredFiles, key) {
			return fmt.Errorf("deployment declares unknown file %s", key)
		}
	}
	for _, key := range requiredFiles {
		if err := ctx.Err(); err != nil {
			return err
		}
		entry, ok := manifest.Files[key]
		if !ok || len(entry.SHA256) != 64 {
			return fmt.Errorf("deployment file %s missing", key)
		}
		got, e := fileDigest(entry.Path)
		if e != nil {
			return fmt.Errorf("deployment file %s: %w", key, e)
		}
		if got != entry.SHA256 {
			return fmt.Errorf("deployment file %s changed", key)
		}
	}
	// 同样是双向覆盖。要求的资源组与控制文件由代码规定——「一个合格的部署长什么样」不能交给
	// 被校验的那份清单自己说；清单负责声明各项的期望值，代码负责核对它们确实被设了界限。
	verified := map[string]bool{}
	for _, group := range requiredGroups {
		for _, name := range requiredLimits {
			path := "/sys/fs/cgroup/" + group + "/" + name
			expected, ok := manifest.Limits[path]
			if !ok || expected == "" || strings.Contains(expected, "max") || (name == "memory.swap.max" && expected != "0") {
				return fmt.Errorf("deployment limit %s missing or unbounded", path)
			}
			actual, e := readLimit(path)
			if e != nil {
				return fmt.Errorf("deployment limit %s: %w", path, e)
			}
			if strings.TrimSpace(string(actual)) != expected {
				return fmt.Errorf("deployment limit %s differs", path)
			}
			verified[path] = true
		}
	}
	for path := range manifest.Limits {
		if !verified[path] {
			return fmt.Errorf("deployment declares limit %s that is never verified", path)
		}
	}
	return nil
}

func protectedDigest(path string) (string, error) {
	f, err := openProtected(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	n, err := io.Copy(h, io.LimitReader(f, (256<<20)+1))
	if err != nil {
		return "", err
	}
	if n > 256<<20 {
		return "", fmt.Errorf("deployment file too large")
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// No mutable ancestor or symlink may select an installation file. With every
// ancestor root-owned and non-writable, unprivileged peers cannot rebind paths.
func openProtected(path string) (*os.File, error) {
	if !filepath.IsAbs(path) || filepath.Clean(path) != path {
		return nil, fmt.Errorf("deployment path must be absolute and clean")
	}
	for p := path; p != "/"; p = filepath.Dir(p) {
		st, err := os.Lstat(p)
		if err != nil {
			return nil, err
		}
		sys, ok := st.Sys().(*syscall.Stat_t)
		if !ok || sys.Uid != 0 || st.Mode().Perm()&0022 != 0 || st.Mode()&os.ModeSymlink != 0 || (p == path && sys.Nlink != 1) || (p != path && !st.IsDir()) {
			return nil, fmt.Errorf("deployment path is not root-protected: %s", p)
		}
	}
	f, err := os.OpenFile(path, os.O_RDONLY|syscall.O_NOFOLLOW|syscall.O_NONBLOCK, 0)
	if err != nil {
		return nil, err
	}
	st, err := f.Stat()
	if err != nil || !st.Mode().IsRegular() {
		f.Close()
		return nil, fmt.Errorf("deployment file is not regular: %s", path)
	}
	return f, nil
}
