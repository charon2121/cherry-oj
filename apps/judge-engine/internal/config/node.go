package config

import (
	"fmt"
	"net/url"
	"regexp"
	"time"
)

// NodeConfig 显式启用节点控制链路；禁用时保留旧 /judge 联调入口。
type NodeConfig struct {
	Architecture        string   `yaml:"-"`
	RuntimeDigest       string   `yaml:"-"`
	Enabled             bool     `yaml:"enabled"`
	ID                  string   `yaml:"id"`
	ControlPlaneURL     string   `yaml:"controlPlaneURL"`
	AdvertiseURL        string   `yaml:"advertiseURL"`
	ControlToken        string   `yaml:"controlToken"`
	HeartbeatInterval   Duration `yaml:"heartbeatInterval"`
	RequestTimeout      Duration `yaml:"requestTimeout"`
	CPUModel            string   `yaml:"cpuModel"`
	OSVersion           string   `yaml:"osVersion"`
	KernelVersion       string   `yaml:"kernelVersion"`
	SandboxVersion      string   `yaml:"sandboxVersion"`
	ToolchainVersion    string   `yaml:"toolchainVersion"`
	MaxArchiveBytes     int64    `yaml:"maxArchiveBytes"`
	MaxExpandedBytes    int64    `yaml:"maxExpandedBytes"`
	MaxEntryBytes       int64    `yaml:"maxEntryBytes"`
	MaxFiles            int      `yaml:"maxFiles"`
	MaxCompressionRatio int64    `yaml:"maxCompressionRatio"`
}

func defaultNode() NodeConfig {
	return NodeConfig{ID: "judge-local-1", ControlPlaneURL: "http://127.0.0.1:8084", AdvertiseURL: "http://127.0.0.1:5051",
		HeartbeatInterval: Duration(10 * time.Second), RequestTimeout: Duration(5 * time.Second), CPUModel: "local", OSVersion: "linux",
		KernelVersion: "local", SandboxVersion: "0.1.0-mvp", ToolchainVersion: "g++",
		MaxArchiveBytes: 100 << 20, MaxExpandedBytes: 1 << 30, MaxEntryBytes: 64 << 20, MaxFiles: 2000, MaxCompressionRatio: 100}
}
func (n NodeConfig) Validate() error {
	if !n.Enabled {
		return nil
	}
	if !regexp.MustCompile(`^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$`).MatchString(n.ID) || n.ControlToken == "" {
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
	if n.CPUModel == "" || n.OSVersion == "" || n.KernelVersion == "" || n.SandboxVersion == "" || n.ToolchainVersion == "" {
		return fmt.Errorf("node environment metadata required")
	}
	return nil
}
