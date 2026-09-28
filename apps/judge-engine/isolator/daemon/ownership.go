//go:build linux && amd64

package daemon

import (
	"fmt"
	"os"
	"path/filepath"

	"golang.org/x/sys/unix"
)

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
