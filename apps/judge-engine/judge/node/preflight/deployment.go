package preflight

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"slices"
	"syscall"

	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/node/wire"
)

// root 管理的部署清单：绑定不可变的发布文件。
// 它是本机部署元数据，不是对外协议，也不上报控制面。
type deploymentManifest struct {
	Version      int                       `json:"version"`
	Backend      string                    `json:"backend"`
	Architecture string                    `json:"architecture"`
	Files        map[string]deploymentFile `json:"files"`
}
type deploymentFile struct {
	Path   string `json:"path"`
	SHA256 string `json:"sha256"`
}

func checkDeployment(ctx context.Context, j config.Settings) error {
	// 执行层在本进程内，天然就是本机同批安装的那一个；这里只核对平台与部署清单。
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
	// 服务实际启动的是 current 下的路径；它解析到的文件必须就是清单声明的那一个，
	// 否则校验的是清单里那份、跑的是另一份。
	for key, relative := range releaseFiles {
		active, err := filepath.EvalSymlinks(filepath.Join(releaseDir, relative))
		if err != nil || active != manifest.Files[key].Path {
			return fmt.Errorf("active release differs from deployment file %s", key)
		}
	}
	return verifyManifest(ctx, manifest, protectedDigest)
}

func verifyManifest(ctx context.Context, manifest deploymentManifest, fileDigest func(string) (string, error)) error {
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
