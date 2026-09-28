//go:build linux && amd64

package daemon

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"

	"golang.org/x/sys/unix"
)

// ManifestEntry 对应 rootfs 制作器的文件记录；模式、链接目标或内容任一变化都需重新固定摘要。
type ManifestEntry struct {
	Path   string
	SHA256 string
	Link   string
	Mode   uint32
}

// Manifest 的可信性来自 Config 固定的整份清单摘要，不能只信清单自己声明的文件哈希。
type Manifest struct {
	Version int
	Source  string
	Entries []ManifestEntry
}

// verifyRoot 在启动时核验 rootfs 与固定摘要的清单逐文件一致；多一个或少一个文件都拒绝启动。
func verifyRoot(c Config) error {
	if err := securePath(c.RootFS, true); err != nil {
		return err
	}
	entries, err := readManifest(c)
	if err != nil {
		return err
	}
	if err = compareTree(c.RootFS, entries); err != nil {
		return err
	}
	return checkMountPoints(c.RootFS)
}

// readManifest 只接受摘要与 Config 固定值一致的清单，返回按路径索引的条目。
func readManifest(c Config) (map[string]ManifestEntry, error) {
	if err := securePath(c.ManifestPath, false); err != nil {
		return nil, err
	}
	f, err := os.Open(c.ManifestPath)
	if err != nil {
		return nil, err
	}
	data, err := io.ReadAll(io.LimitReader(f, 8<<20+1))
	f.Close()
	if err != nil {
		return nil, err
	}
	if len(data) > 8<<20 {
		return nil, fmt.Errorf("manifest is too large")
	}
	h := sha256.Sum256(data)
	if hex.EncodeToString(h[:]) != c.ManifestSHA256 {
		return nil, fmt.Errorf("manifest digest mismatch")
	}
	var m Manifest
	if err = json.Unmarshal(data, &m); err != nil {
		return nil, err
	}
	if m.Version != 1 || m.Source == "" || len(m.Entries) == 0 {
		return nil, fmt.Errorf("manifest is missing version, source or files")
	}
	entries := map[string]ManifestEntry{}
	for _, e := range m.Entries {
		if e.Path == "." || !fs.ValidPath(e.Path) || entries[e.Path].Path != "" {
			return nil, fmt.Errorf("invalid manifest path")
		}
		entries[e.Path] = e
	}
	return entries, nil
}

// compareTree 同时拒绝额外文件和缺失文件；只检查清单列出的文件会漏掉 rootfs 中新增的内容。
func compareTree(root string, entries map[string]ManifestEntry) error {
	err := filepath.WalkDir(root, func(p string, d fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if p == root {
			return nil
		}
		rel, err := filepath.Rel(root, p)
		if err != nil {
			return err
		}
		entry, ok := entries[rel]
		if !ok {
			return fmt.Errorf("rootfs contains unlisted file %s", rel)
		}
		delete(entries, rel)
		return checkEntry(p, rel, entry)
	})
	if err != nil {
		return err
	}
	if len(entries) != 0 {
		return fmt.Errorf("rootfs is missing manifest file")
	}
	return nil
}

// checkEntry 核对单个文件的所有者、权限位、类型，以及链接目标或内容摘要。
func checkEntry(p, rel string, entry ManifestEntry) error {
	var st unix.Stat_t
	if err := unix.Lstat(p, &st); err != nil {
		return err
	}
	if st.Uid != 0 || st.Mode&0022 != 0 && st.Mode&unix.S_IFMT != unix.S_IFLNK {
		return fmt.Errorf("wrong rootfs permissions %s", rel)
	}
	if st.Mode&07777 != entry.Mode {
		return fmt.Errorf("rootfs mode mismatch %s", rel)
	}
	switch st.Mode & unix.S_IFMT {
	case unix.S_IFDIR:
		if entry.SHA256 != "" || entry.Link != "" {
			return fmt.Errorf("invalid directory manifest")
		}
	case unix.S_IFLNK:
		link, err := os.Readlink(p)
		if err != nil {
			return err
		}
		if link != entry.Link {
			return fmt.Errorf("link mismatch")
		}
	case unix.S_IFREG:
		if st.Nlink != 1 {
			return fmt.Errorf("rootfs refuses hard links")
		}
		f, err := os.Open(p)
		if err != nil {
			return err
		}
		hash := sha256.New()
		_, err = io.Copy(hash, f)
		ce := f.Close()
		if err != nil {
			return err
		}
		if ce != nil {
			return ce
		}
		if hex.EncodeToString(hash.Sum(nil)) != entry.SHA256 {
			return fmt.Errorf("wrong rootfs file digest %s", rel)
		}
	default:
		return fmt.Errorf("rootfs refuses special files")
	}
	return nil
}

// checkMountPoints 确认 P4 要挂载的目标都是 rootfs 制作器预建的目录，启动器入口只有 root 可达。
func checkMountPoints(root string) error {
	for _, p := range []string{"work", "tmp", "proc", "dev", ".oldroot", ".sandbox"} {
		st, err := os.Lstat(filepath.Join(root, p))
		if err != nil {
			return err
		}
		if !st.IsDir() {
			return fmt.Errorf("mount point is not a directory")
		}
	}
	st, err := os.Lstat(filepath.Join(root, ".sandbox/launcher"))
	if err != nil {
		return err
	}
	if !st.Mode().IsRegular() {
		return fmt.Errorf("launcher mount point must be a regular file")
	}
	// root 专有目录保留可信 re-exec 启动器；降权后的 payload 不能访问该入口。
	if st, err = os.Stat(filepath.Join(root, ".sandbox")); err != nil || st.Mode().Perm() != 0700 {
		return fmt.Errorf(".sandbox directory must be root 0700")
	}
	return nil
}
