package config

import (
	"fmt"
	"net/url"
	"regexp"
	"time"

	platform "cherry-oj/judge-engine/internal/platform/config"
)

// nodeIDPattern 在包级编译一次；Validate 每次启动都会调用，没有必要重复编译。
var nodeIDPattern = regexp.MustCompile(`^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$`)

// Node 显式启用节点控制链路；禁用时保留旧 /judge 联调入口。
type Node struct {
	// DeploymentManifest 是 root 管理的原生 Linux 部署清单，启动自检据此核对发布文件与资源上界；
	// 不由任何判题请求提供。
	DeploymentManifest  string            `yaml:"deploymentManifest"`
	Enabled             bool              `yaml:"enabled"`
	ID                  string            `yaml:"id"`
	ControlPlaneURL     string            `yaml:"controlPlaneURL"`
	AdvertiseURL        string            `yaml:"advertiseURL"`
	ControlToken        string            `yaml:"controlToken"`
	HeartbeatInterval   platform.Duration `yaml:"heartbeatInterval"`
	RequestTimeout      platform.Duration `yaml:"requestTimeout"`
	MaxArchiveBytes     int64             `yaml:"maxArchiveBytes"`
	MaxExpandedBytes    int64             `yaml:"maxExpandedBytes"`
	MaxEntryBytes       int64             `yaml:"maxEntryBytes"`
	MaxFiles            int               `yaml:"maxFiles"`
	MaxCompressionRatio int64             `yaml:"maxCompressionRatio"`
}

func defaultNode() Node {
	return Node{ID: "judge-local-1", ControlPlaneURL: "http://127.0.0.1:8084", AdvertiseURL: "http://127.0.0.1:5051",
		HeartbeatInterval: platform.Duration(10 * time.Second), RequestTimeout: platform.Duration(5 * time.Second),
		MaxArchiveBytes: 100 << 20, MaxExpandedBytes: 1 << 30, MaxEntryBytes: 64 << 20, MaxFiles: 2000, MaxCompressionRatio: 100}
}
func (n Node) Validate() error {
	if !n.Enabled {
		return nil
	}
	if !nodeIDPattern.MatchString(n.ID) || n.ControlToken == "" {
		return fmt.Errorf("node requires a valid id and control token")
	}
	for _, value := range []string{n.ControlPlaneURL, n.AdvertiseURL} {
		u, e := url.Parse(value)
		if e != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Hostname() == "" || u.User != nil || u.RawQuery != "" || u.Fragment != "" || (u.Path != "" && u.Path != "/") {
			return fmt.Errorf("node URLs must be HTTP(S) origins")
		}
	}
	if n.HeartbeatInterval <= 0 || n.HeartbeatInterval.Std() > time.Minute || n.RequestTimeout <= 0 || n.MaxArchiveBytes <= 0 || n.MaxExpandedBytes <= 0 || n.MaxEntryBytes <= 0 || n.MaxFiles < 2 || n.MaxFiles > 2000 || n.MaxCompressionRatio <= 0 {
		return fmt.Errorf("invalid node intervals or install limits")
	}
	return nil
}
