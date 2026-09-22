package probe

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/url"
	"os"
	"path/filepath"
	"runtime"
	"slices"
	"sort"
	"strings"
	"syscall"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/config"
	"cherry-oj/judge-engine/judge/internal/node/identity"
	"cherry-oj/judge-engine/judge/internal/node/wire"
)

// The root-owned installation record binds immutable release files and the
// live cgroup limits. It is local deployment metadata, not a public protocol.
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

func probeDeployment(ctx context.Context, j config.Settings, version string, sandbox Sandbox) (identity.Environment, error) {
	// 原生部署下 sandbox 必须是本机同批安装的那一个：跨主机的端点不受这份部署清单约束，
	// 校验清单就证明不了实际执行环境。这里只要求回环地址，具体端口由部署配置决定。
	if err := requireLoopback(j.SandboxURL); err != nil {
		return identity.Environment{}, err
	}
	if runtime.GOOS != "linux" || runtime.GOARCH != "amd64" {
		return identity.Environment{}, fmt.Errorf("native deployment requires Linux/amd64")
	}
	digest, err := verifyDeployment(ctx, j.Node.DeploymentManifest)
	if err != nil {
		return identity.Environment{}, err
	}
	spec := contract.RunSpec{Command: []string{"g++", "--version"}, Limits: contract.Limits{CPUNs: 2_000_000_000, ClockNs: 5_000_000_000, MemoryBytes: 128 << 20, MaxProcesses: 64, StdoutMaxBytes: 8192, StderrMaxBytes: 1024}}
	result, err := sandbox.Probe(ctx, spec)
	if err != nil {
		return identity.Environment{}, err
	}
	if result.Status != contract.StatusOK || result.ExitCode != 0 {
		return identity.Environment{}, fmt.Errorf("isolated compiler probe failed")
	}
	compiler := strings.SplitN(result.Stdout, "\n", 2)[0]
	if compiler == "" || len(compiler) > 128 {
		return identity.Environment{}, fmt.Errorf("compiler identity invalid")
	}
	cpu, err := os.ReadFile("/proc/cpuinfo")
	if err != nil {
		return identity.Environment{}, err
	}
	model, err := cpuIdentity(string(cpu))
	if err != nil {
		return identity.Environment{}, err
	}
	kernel, err := os.ReadFile("/proc/sys/kernel/osrelease")
	if err != nil {
		return identity.Environment{}, err
	}
	osRelease, err := os.ReadFile("/etc/os-release")
	if err != nil {
		return identity.Environment{}, err
	}
	release := ""
	for _, line := range strings.Split(string(osRelease), "\n") {
		if strings.HasPrefix(line, "PRETTY_NAME=") {
			release = strings.Trim(strings.TrimPrefix(line, "PRETTY_NAME="), "\"")
			break
		}
	}
	if release == "" || len(release) > 128 || len(strings.TrimSpace(string(kernel))) > 128 {
		return identity.Environment{}, fmt.Errorf("host environment metadata invalid")
	}
	return identity.Environment{
		Architecture:     runtime.GOARCH,
		CPUModel:         model,
		OSVersion:        release,
		KernelVersion:    strings.TrimSpace(string(kernel)),
		SandboxVersion:   version + "/linux",
		ToolchainVersion: compiler,
		RuntimeDigest:    digest,
	}, nil
}

func cpuIdentity(cpu string) (string, error) {
	keys := map[string]bool{"model name": true, "flags": true, "Features": true, "CPU implementer": true, "CPU part": true, "CPU revision": true, "CPU architecture": true}
	facts := map[string]bool{}
	model := ""
	for _, line := range strings.Split(cpu, "\n") {
		k, v, ok := strings.Cut(line, ":")
		k, v = strings.TrimSpace(k), strings.TrimSpace(v)
		if ok && keys[k] {
			facts[k+":"+v] = true
			if k == "model name" {
				model = v
			}
		}
	}
	if len(facts) == 0 {
		return "", fmt.Errorf("CPU identity unavailable")
	}
	ordered := make([]string, 0, len(facts))
	for f := range facts {
		ordered = append(ordered, f)
	}
	sort.Strings(ordered)
	sum := sha256.Sum256([]byte(strings.Join(ordered, "\n")))
	if len(model) > 160 {
		model = model[:160]
	}
	return model + " sha256:" + hex.EncodeToString(sum[:]), nil
}

func verifyDeployment(ctx context.Context, path string) (string, error) {
	file, err := openProtected(path)
	if err != nil {
		return "", err
	}
	defer file.Close()
	var manifest deploymentManifest
	if err = wire.Decode(io.LimitReader(file, 65537), &manifest); err != nil {
		return "", fmt.Errorf("deployment manifest: %w", err)
	}
	// 服务实际启动的是 releaseBinDir 下的符号链接；它指向的文件必须就是清单声明的那一个，
	// 否则校验的是清单里那份、跑的是另一份。
	for key, name := range map[string]string{"sandbox": "sandbox", "helper": "sandbox-helper"} {
		active, err := filepath.EvalSymlinks(filepath.Join(releaseBinDir, name))
		if err != nil || active != manifest.Files[key].Path {
			return "", fmt.Errorf("active release differs from deployment file %s", key)
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

func verifyManifest(ctx context.Context, manifest deploymentManifest, fileDigest func(string) (string, error), readLimit func(string) ([]byte, error)) (string, error) {
	if manifest.Version != 1 || manifest.Backend != "linux" || manifest.Architecture != "amd64" {
		return "", fmt.Errorf("unsupported deployment manifest")
	}
	// 双向覆盖：要求的每一项都必须在清单里，清单里的每一项也都必须是要求的。
	// 只比数量的话，多一项少一项会互相抵消，而报错也说不出是哪一项。
	for key := range manifest.Files {
		if !slices.Contains(requiredFiles, key) {
			return "", fmt.Errorf("deployment declares unknown file %s", key)
		}
	}
	for _, key := range requiredFiles {
		if err := ctx.Err(); err != nil {
			return "", err
		}
		entry, ok := manifest.Files[key]
		if !ok || len(entry.SHA256) != 64 {
			return "", fmt.Errorf("deployment file %s missing", key)
		}
		got, e := fileDigest(entry.Path)
		if e != nil {
			return "", fmt.Errorf("deployment file %s: %w", key, e)
		}
		if got != entry.SHA256 {
			return "", fmt.Errorf("deployment file %s changed", key)
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
				return "", fmt.Errorf("deployment limit %s missing or unbounded", path)
			}
			actual, e := readLimit(path)
			if e != nil {
				return "", fmt.Errorf("deployment limit %s: %w", path, e)
			}
			if strings.TrimSpace(string(actual)) != expected {
				return "", fmt.Errorf("deployment limit %s differs", path)
			}
			verified[path] = true
		}
	}
	for path := range manifest.Limits {
		if !verified[path] {
			return "", fmt.Errorf("deployment declares limit %s that is never verified", path)
		}
	}
	b, err := json.Marshal(manifest)
	if err != nil {
		return "", err
	}
	sum := sha256.Sum256(b)
	return hex.EncodeToString(sum[:]), nil
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
