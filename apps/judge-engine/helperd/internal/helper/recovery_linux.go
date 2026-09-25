//go:build linux && amd64

package helper

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"golang.org/x/sys/unix"
)

// recoverOwned 仅在 Serve 持有独占服务锁后调用。所有权标记绑定 JobsDir，
// 无标记的非空目录或未知条目不能被当成上次任务残留清理。
func recoverOwned(ctx context.Context, c Config) error {
	entries, err := os.ReadDir(c.JobsDir)
	if err != nil {
		return err
	}
	marker := filepath.Join(c.StateDir, "owner-v1")
	want := "cherry-sandbox-helper-v1\n" + c.JobsDir + "\n"
	data, err := os.ReadFile(marker)
	if errors.Is(err, os.ErrNotExist) {
		for _, e := range entries {
			if e.IsDir() {
				return fmt.Errorf("no ownership marker but jobs is not empty")
			}
		}
		f, e := os.OpenFile(marker, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
		if e != nil {
			return e
		}
		_, e = f.WriteString(want)
		se := f.Sync()
		ce := f.Close()
		if e = errors.Join(e, se, ce); e != nil {
			return e
		}
	} else if err != nil {
		return err
	} else if string(data) != want {
		return fmt.Errorf("the ownership marker disagrees with jobs")
	}
	if err = securePath(marker, false); err != nil {
		return err
	}
	root, err := os.OpenRoot(c.JobsDir)
	if err != nil {
		return err
	}
	defer root.Close()
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		if !runName(e.Name()) {
			return fmt.Errorf("jobs contains unknown group %s", e.Name())
		}
		if err = securePath(filepath.Join(c.JobsDir, e.Name()), true); err != nil {
			return err
		}
		g, err := root.OpenRoot(e.Name())
		if err != nil {
			return err
		}
		err = recoverGroup(ctx, g)
		ce := g.Close()
		if err = errors.Join(err, ce); err != nil {
			return err
		}
		if err = root.Remove(e.Name()); err != nil {
			return err
		}
	}
	entries, err = os.ReadDir(c.StateDir)
	if err != nil {
		return err
	}
	for _, e := range entries {
		if e.Name() == "owner-v1" || e.Name() == "lock" {
			continue
		}
		p := filepath.Join(c.StateDir, e.Name())
		if e.Name() == filepath.Base(c.SocketPath) {
			var st unix.Stat_t
			if err = unix.Lstat(p, &st); err != nil {
				return err
			}
			if st.Mode&unix.S_IFMT != unix.S_IFSOCK || st.Uid != 0 {
				return fmt.Errorf("stale socket has the wrong type or ownership")
			}
			if err = os.Remove(p); err != nil {
				return err
			}
			continue
		}
		if !runName(e.Name()) || !e.IsDir() {
			return fmt.Errorf("state contains unknown entry %s", e.Name())
		}
		if err = securePath(p, true); err != nil {
			return err
		}
		// 挂载只存在子 namespace；宿主目录必须为空，绝不递归清理未知数据。
		if err = os.Remove(p); err != nil {
			return err
		}
	}
	return nil
}

// recoverGroup 处理失去 Go 对象的历史执行组；写 kill 只是请求，
// 只有 populated=0 才允许调用者删除组目录，未知嵌套组会阻止恢复。
func recoverGroup(ctx context.Context, g *os.Root) error {
	d, err := g.Open(".")
	if err != nil {
		return err
	}
	entries, err := d.ReadDir(-1)
	d.Close()
	if err != nil {
		return err
	}
	for _, e := range entries {
		if e.IsDir() {
			return fmt.Errorf("a leftover job group contains an unknown nested group")
		}
	}
	kill, err := g.OpenFile("cgroup.kill", os.O_WRONLY, 0)
	if err != nil {
		return err
	}
	_, err = kill.WriteString("1")
	ce := kill.Close()
	if err = errors.Join(err, ce); err != nil {
		return err
	}
	ticker := time.NewTicker(5 * time.Millisecond)
	defer ticker.Stop()
	for {
		f, err := g.Open("cgroup.events")
		if err != nil {
			return err
		}
		b, err := io.ReadAll(io.LimitReader(f, 4097))
		f.Close()
		if err != nil {
			return err
		}
		if len(b) > 4096 {
			return fmt.Errorf("events is too large")
		}
		value := ""
		for _, line := range strings.Split(string(b), "\n") {
			fields := strings.Fields(line)
			if len(fields) == 2 && fields[0] == "populated" {
				if value != "" {
					return fmt.Errorf("duplicate populated")
				}
				value = fields[1]
			}
		}
		if value == "0" {
			return nil
		}
		if value != "1" {
			return fmt.Errorf("invalid populated")
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
		}
	}
}
