//go:build linux && amd64

package daemon

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
)

// Config 来自 root 管理的本机文件；客户端请求不能选择路径、身份或并发槽位。
type Config struct {
	SocketPath, StateDir, JobsDir, RootFS, ManifestPath, ManifestSHA256 string
	ServiceUID, ServiceGID, PayloadUID, PayloadGID, InitUID, InitGID    int
	Parallelism                                                         int
}

// Validate 核对身份互斥及路径形状；文件所有权和 rootfs 完整性还需 Serve 的安装检查。
func (c Config) Validate() error {
	for _, p := range []string{c.SocketPath, c.StateDir, c.JobsDir, c.RootFS, c.ManifestPath} {
		if !filepath.IsAbs(p) || filepath.Clean(p) != p || p == "/" {
			return fmt.Errorf("helper paths must be explicit absolute paths")
		}
	}
	if filepath.Dir(c.SocketPath) != c.StateDir {
		return fmt.Errorf("the socket must live in the exclusive state directory")
	}
	if c.ServiceGID <= 0 || c.ServiceUID <= 0 || c.PayloadUID <= 0 || c.PayloadGID <= 0 || c.InitUID <= 0 || c.InitGID <= 0 || c.ServiceUID == c.PayloadUID || c.ServiceUID == c.InitUID || c.PayloadUID == c.InitUID || c.PayloadGID == c.InitGID || c.ServiceGID == c.PayloadGID || c.ServiceGID == c.InitGID {
		return fmt.Errorf("service, init and payload must use separate non-root identities")
	}
	for _, id := range []int{c.ServiceUID, c.ServiceGID, c.PayloadUID, c.PayloadGID, c.InitUID, c.InitGID} {
		if uint64(id) >= 1<<32-1 {
			return fmt.Errorf("identity out of range")
		}
	}
	if c.Parallelism < 1 || c.Parallelism > 4 {
		return fmt.Errorf("concurrency must be 1 to 4")
	}
	if err := c.validateSlotIdentities(); err != nil {
		return err
	}
	if len(c.ManifestSHA256) != 64 {
		return fmt.Errorf("the rootfs manifest digest must be pinned")
	}
	return nil
}

// LoadConfig 只读取 root 控制的本机配置，路径及祖先目录必须不可被非 root 替换。
// 它不证明 rootfs 可用；Serve 仍需校验安装并执行探测。
func LoadConfig(path string) (Config, error) {
	var c Config
	if !filepath.IsAbs(path) {
		return c, fmt.Errorf("configuration requires an absolute path")
	}
	if err := checkConfigPath(path); err != nil {
		return c, err
	}
	f, err := os.Open(path)
	if err != nil {
		return c, err
	}
	defer f.Close()
	st, err := f.Stat()
	if err != nil {
		return c, err
	}
	if !st.Mode().IsRegular() || st.Size() > 64<<10 {
		return c, fmt.Errorf("configuration is not a bounded regular file")
	}
	d := json.NewDecoder(f)
	d.DisallowUnknownFields()
	if err = d.Decode(&c); err != nil {
		return c, err
	}
	var extra any
	if d.Decode(&extra) != io.EOF {
		return c, fmt.Errorf("configuration has trailing content")
	}
	return c, c.Validate()
}
