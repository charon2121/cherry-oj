//go:build linux && amd64

package helper

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

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

// securePath 同时核验祖先目录；只检查末端文件会遗漏可由其他身份替换路径的父目录。
func securePath(p string, dir bool) error {
	for cur := p; ; cur = filepath.Dir(cur) {
		st, err := os.Lstat(cur)
		if err != nil {
			return err
		}
		var stat unix.Stat_t
		if err = unix.Lstat(cur, &stat); err != nil {
			return err
		}
		if stat.Uid != 0 || st.Mode()&0022 != 0 || st.Mode()&os.ModeSymlink != 0 {
			return fmt.Errorf("path must be owned by root and not writable by non-root: %s", cur)
		}
		if cur == p && dir && !st.IsDir() {
			return fmt.Errorf("a directory is required: %s", cur)
		}
		if cur == "/" {
			break
		}
	}
	return nil
}
func verifyRoot(c Config) error {
	if err := securePath(c.RootFS, true); err != nil {
		return err
	}
	if err := securePath(c.ManifestPath, false); err != nil {
		return err
	}
	f, err := os.Open(c.ManifestPath)
	if err != nil {
		return err
	}
	data, err := io.ReadAll(io.LimitReader(f, 8<<20+1))
	f.Close()
	if err != nil {
		return err
	}
	if len(data) > 8<<20 {
		return fmt.Errorf("manifest is too large")
	}
	h := sha256.Sum256(data)
	if hex.EncodeToString(h[:]) != c.ManifestSHA256 {
		return fmt.Errorf("manifest digest mismatch")
	}
	var m Manifest
	if err = json.Unmarshal(data, &m); err != nil {
		return err
	}
	if m.Version != 1 || m.Source == "" || len(m.Entries) == 0 {
		return fmt.Errorf("manifest is missing version, source or files")
	}
	entries := map[string]ManifestEntry{}
	for _, e := range m.Entries {
		if e.Path == "." || !fs.ValidPath(e.Path) || entries[e.Path].Path != "" {
			return fmt.Errorf("invalid manifest path")
		}
		entries[e.Path] = e
	}
	// 同时拒绝额外文件和缺失文件；只检查清单列出的文件会漏掉 rootfs 中新增的内容。
	err = filepath.WalkDir(c.RootFS, func(p string, d fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if p == c.RootFS {
			return nil
		}
		rel, err := filepath.Rel(c.RootFS, p)
		if err != nil {
			return err
		}
		entry, ok := entries[rel]
		if !ok {
			return fmt.Errorf("rootfs contains unlisted file %s", rel)
		}
		delete(entries, rel)
		var st unix.Stat_t
		if err = unix.Lstat(p, &st); err != nil {
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
	})
	if err != nil {
		return err
	}
	if len(entries) != 0 {
		return fmt.Errorf("rootfs is missing manifest file")
	}
	for _, p := range []string{"work", "tmp", "proc", "dev", ".oldroot", ".sandbox"} {
		st, err := os.Lstat(filepath.Join(c.RootFS, p))
		if err != nil {
			return err
		}
		if !st.IsDir() {
			return fmt.Errorf("mount point is not a directory")
		}
	}
	st, err := os.Lstat(filepath.Join(c.RootFS, ".sandbox/launcher"))
	if err != nil {
		return err
	}
	if !st.Mode().IsRegular() {
		return fmt.Errorf("launcher mount point must be a regular file")
	}
	// root 专有目录保留可信 re-exec 启动器；降权后的 payload 不能访问该入口。
	if st, err = os.Stat(filepath.Join(c.RootFS, ".sandbox")); err != nil || st.Mode().Perm() != 0700 {
		return fmt.Errorf(".sandbox directory must be root 0700")
	}
	return nil
}
func runName(n string) bool {
	if !strings.HasPrefix(n, "run-") || len(n) != 36 {
		return false
	}
	b, err := hex.DecodeString(n[4:])
	return err == nil && len(b) == 16
}
