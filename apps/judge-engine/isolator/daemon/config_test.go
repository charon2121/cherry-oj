//go:build linux && amd64

package daemon

import (
	"bytes"
	"testing"
)

func TestConfigRejectsPrivilegeConfusion(t *testing.T) {
	good := Config{SocketPath: "/run/cherry-test/helper.sock", StateDir: "/run/cherry-test", JobsDir: "/sys/fs/cgroup/cherry-test/jobs", RootFS: "/opt/cherry-test/rootfs", ManifestPath: "/opt/cherry-test/manifest.json", ManifestSHA256: string(bytes.Repeat([]byte{'a'}, 64)), ServiceUID: 1001, ServiceGID: 1001, PayloadUID: 1002, PayloadGID: 1002, InitUID: 1003, InitGID: 1003, Parallelism: 1}
	if err := good.Validate(); err != nil {
		t.Fatal(err)
	}
	for _, change := range []func(*Config){func(c *Config) { c.PayloadUID = c.InitUID }, func(c *Config) { c.PayloadUID = 0 }, func(c *Config) { c.ServiceUID = c.PayloadUID }, func(c *Config) { c.SocketPath = "/tmp/other.sock" }, func(c *Config) { c.Parallelism = 0 }, func(c *Config) { c.ManifestSHA256 = "" }} {
		c := good
		change(&c)
		if c.Validate() == nil {
			t.Fatal("接受错误配置")
		}
	}
}
