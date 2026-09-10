package node

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"syscall"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
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

func probeDeployment(ctx context.Context, j config.JudgeConfig, version string, request func(string, any, any) error) (config.JudgeConfig, error) {
	if j.SandboxURL != "http://127.0.0.1:15050" {
		return j, fmt.Errorf("native deployment requires the local managed sandbox endpoint")
	}
	if runtime.GOOS != "linux" || runtime.GOARCH != "amd64" {
		return j, fmt.Errorf("native deployment requires Linux/amd64")
	}
	digest, err := verifyDeployment(ctx, j.Node.DeploymentManifest)
	if err != nil {
		return j, err
	}
	var result contract.RunResult
	spec := contract.RunSpec{Command: []string{"g++", "--version"}, Limits: contract.Limits{CPUNs: 2_000_000_000, ClockNs: 5_000_000_000, MemoryBytes: 128 << 20, MaxProcesses: 64, StdoutMaxBytes: 8192, StderrMaxBytes: 1024}}
	if err = request("/run", spec, &result); err != nil {
		return j, err
	}
	if result.Status != contract.StatusOK || result.ExitCode != 0 {
		return j, fmt.Errorf("isolated compiler probe failed")
	}
	compiler := strings.SplitN(result.Stdout, "\n", 2)[0]
	if compiler == "" || len(compiler) > 128 {
		return j, fmt.Errorf("compiler identity invalid")
	}
	cpu, err := os.ReadFile("/proc/cpuinfo")
	if err != nil {
		return j, err
	}
	model, err := cpuIdentity(string(cpu))
	if err != nil {
		return j, err
	}
	kernel, err := os.ReadFile("/proc/sys/kernel/osrelease")
	if err != nil {
		return j, err
	}
	osRelease, err := os.ReadFile("/etc/os-release")
	if err != nil {
		return j, err
	}
	release := ""
	for _, line := range strings.Split(string(osRelease), "\n") {
		if strings.HasPrefix(line, "PRETTY_NAME=") {
			release = strings.Trim(strings.TrimPrefix(line, "PRETTY_NAME="), "\"")
			break
		}
	}
	if release == "" || len(release) > 128 || len(strings.TrimSpace(string(kernel))) > 128 {
		return j, fmt.Errorf("host environment metadata invalid")
	}
	j.Node.Architecture, j.Node.CPUModel = runtime.GOARCH, model
	j.Node.OSVersion, j.Node.KernelVersion = release, strings.TrimSpace(string(kernel))
	j.Node.SandboxVersion, j.Node.ToolchainVersion, j.Node.RuntimeDigest = version+"/linux", compiler, digest
	return j, nil
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
	if err = decodeJSON(io.LimitReader(file, 65537), &manifest); err != nil {
		return "", fmt.Errorf("deployment manifest: %w", err)
	}
	for key, name := range map[string]string{"sandbox": "sandbox", "helper": "sandbox-helper"} {
		active, err := filepath.EvalSymlinks("/var/lib/cherry-sandbox/current/bin/" + name)
		if err != nil || active != manifest.Files[key].Path {
			return "", fmt.Errorf("active release differs from deployment file %s", key)
		}
	}
	return verifyManifest(ctx, manifest, protectedDigest, os.ReadFile)
}

func verifyManifest(ctx context.Context, manifest deploymentManifest, fileDigest func(string) (string, error), readLimit func(string) ([]byte, error)) (string, error) {
	if manifest.Version != 1 || manifest.Backend != "linux" || manifest.Architecture != "amd64" {
		return "", fmt.Errorf("unsupported deployment manifest")
	}
	required := []string{"sandbox", "helper", "rootfsManifest", "toolchainLock", "helperConfig", "sandboxConfig", "slice", "helperUnit", "sandboxUnit", "judgeUnit", "bootstrap"}
	if len(manifest.Files) != len(required) {
		return "", fmt.Errorf("deployment file set incomplete")
	}
	for _, key := range required {
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
	// Explicit keys avoid accepting an empty limits map or a caller-selected group.
	for _, group := range []string{"cherry.slice/cherry-sandbox.slice", "cherry.slice/cherry-sandbox.slice/cherry-sandbox-helper.service", "cherry.slice/cherry-sandbox.slice/cherry-sandbox.service", "cherry.slice/cherry-sandbox.slice/cherry-sandbox-judge.service", "cherry.slice/cherry-sandbox.slice/cherry-sandbox-helper.service/supervisor", "cherry.slice/cherry-sandbox.slice/cherry-sandbox-helper.service/jobs"} {
		for _, name := range []string{"cpu.max", "memory.max", "memory.swap.max", "pids.max"} {
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
		}
	}
	if len(manifest.Limits) != 24 {
		return "", fmt.Errorf("unexpected deployment limit set")
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
