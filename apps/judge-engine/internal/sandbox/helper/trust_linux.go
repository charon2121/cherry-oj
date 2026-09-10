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

type ManifestEntry struct {
	Path   string
	SHA256 string
	Link   string
	Mode   uint32
}
type Manifest struct {
	Version int
	Source  string
	Entries []ManifestEntry
}

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
			return fmt.Errorf("路径必须 root 所有且非 root 不可写: %s", cur)
		}
		if cur == p && dir && !st.IsDir() {
			return fmt.Errorf("需要目录: %s", cur)
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
		return fmt.Errorf("manifest 过大")
	}
	h := sha256.Sum256(data)
	if hex.EncodeToString(h[:]) != c.ManifestSHA256 {
		return fmt.Errorf("manifest 摘要不匹配")
	}
	var m Manifest
	if err = json.Unmarshal(data, &m); err != nil {
		return err
	}
	if m.Version != 1 || m.Source == "" || len(m.Entries) == 0 {
		return fmt.Errorf("manifest 缺版本/来源/文件")
	}
	entries := map[string]ManifestEntry{}
	for _, e := range m.Entries {
		if e.Path == "." || !fs.ValidPath(e.Path) || entries[e.Path].Path != "" {
			return fmt.Errorf("manifest 路径错误")
		}
		entries[e.Path] = e
	}
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
			return fmt.Errorf("rootfs 出现未登记文件 %s", rel)
		}
		delete(entries, rel)
		var st unix.Stat_t
		if err = unix.Lstat(p, &st); err != nil {
			return err
		}
		if st.Uid != 0 || st.Mode&0022 != 0 && st.Mode&unix.S_IFMT != unix.S_IFLNK {
			return fmt.Errorf("rootfs 权限错误 %s", rel)
		}
		if st.Mode&07777 != entry.Mode {
			return fmt.Errorf("rootfs mode 不匹配 %s", rel)
		}
		switch st.Mode & unix.S_IFMT {
		case unix.S_IFDIR:
			if entry.SHA256 != "" || entry.Link != "" {
				return fmt.Errorf("目录 manifest 错误")
			}
		case unix.S_IFLNK:
			link, err := os.Readlink(p)
			if err != nil {
				return err
			}
			if link != entry.Link {
				return fmt.Errorf("链接不匹配")
			}
		case unix.S_IFREG:
			if st.Nlink != 1 {
				return fmt.Errorf("rootfs 拒绝硬链接")
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
				return fmt.Errorf("rootfs 文件摘要错误 %s", rel)
			}
		default:
			return fmt.Errorf("rootfs 拒绝特殊文件")
		}
		return nil
	})
	if err != nil {
		return err
	}
	if len(entries) != 0 {
		return fmt.Errorf("rootfs 缺 manifest 文件")
	}
	for _, p := range []string{"work", "tmp", "proc", "dev", ".oldroot", ".sandbox"} {
		st, err := os.Lstat(filepath.Join(c.RootFS, p))
		if err != nil {
			return err
		}
		if !st.IsDir() {
			return fmt.Errorf("挂载点不是目录")
		}
	}
	st, err := os.Lstat(filepath.Join(c.RootFS, ".sandbox/launcher"))
	if err != nil {
		return err
	}
	if !st.Mode().IsRegular() {
		return fmt.Errorf("启动器挂载点必须普通文件")
	}
	if st, err = os.Stat(filepath.Join(c.RootFS, ".sandbox")); err != nil || st.Mode().Perm() != 0700 {
		return fmt.Errorf(".sandbox 目录必须 root 0700")
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
