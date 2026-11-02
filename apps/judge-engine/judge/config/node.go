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
	// DeploymentManifest 是 root 管理的原生 Linux 部署清单，启动自检据此核对发布文件；
	// 不由任何判题请求提供。
	DeploymentManifest string `yaml:"deploymentManifest"`
	// Languages 只用于没有部署清单的开发环境（devhost）：节点声明并实测这些语言。
	// 原生部署不能配置它，语言只从发布里的包锁读，免得一台节点有两个语言来源。
	Languages         []string          `yaml:"languages"`
	Enabled           bool              `yaml:"enabled"`
	ID                string            `yaml:"id"`
	ControlPlaneURL   string            `yaml:"controlPlaneURL"`
	AdvertiseURL      string            `yaml:"advertiseURL"`
	ControlToken      string            `yaml:"controlToken"`
	HeartbeatInterval platform.Duration `yaml:"heartbeatInterval"`
	RequestTimeout    platform.Duration `yaml:"requestTimeout"`
}

func defaultNode() Node {
	return Node{ID: "judge-local-1", ControlPlaneURL: "http://127.0.0.1:8084", AdvertiseURL: "http://127.0.0.1:5051",
		HeartbeatInterval: platform.Duration(10 * time.Second), RequestTimeout: platform.Duration(5 * time.Second)}
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
	if n.HeartbeatInterval <= 0 || n.HeartbeatInterval.Std() > time.Minute || n.RequestTimeout <= 0 {
		return fmt.Errorf("invalid node intervals")
	}
	if n.DeploymentManifest != "" && len(n.Languages) > 0 {
		return fmt.Errorf("node.languages must not be set for a native deployment; languages come from the release's package lock")
	}
	if n.DeploymentManifest == "" && len(n.Languages) == 0 {
		return fmt.Errorf("node.languages is required without a deployment manifest")
	}
	return nil
}
