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
				return fmt.Errorf("无所有权标记但 jobs 非空")
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
		return fmt.Errorf("所有权标记与 jobs 不符")
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
			return fmt.Errorf("jobs 存在未知组 %s", e.Name())
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
				return fmt.Errorf("旧 socket 类型/所有权错误")
			}
			if err = os.Remove(p); err != nil {
				return err
			}
			continue
		}
		if !runName(e.Name()) || !e.IsDir() {
			return fmt.Errorf("state 存在未知条目 %s", e.Name())
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
			return fmt.Errorf("遗留任务组出现未知嵌套组")
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
			return fmt.Errorf("events 过大")
		}
		value := ""
		for _, line := range strings.Split(string(b), "\n") {
			fields := strings.Fields(line)
			if len(fields) == 2 && fields[0] == "populated" {
				if value != "" {
					return fmt.Errorf("重复 populated")
				}
				value = fields[1]
			}
		}
		if value == "0" {
			return nil
		}
		if value != "1" {
			return fmt.Errorf("populated 无效")
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
		}
	}
}
