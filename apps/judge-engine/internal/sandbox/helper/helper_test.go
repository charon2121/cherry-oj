// 白盒用于验证有界 drain 的字节保留和非阻塞溢出信号。
package helper

import (
	"bytes"
	"io"
	"testing"
)

func TestCaptureBoundsAndDrains(t *testing.T) {
	for _, limit := range []int64{0, 1, 1024} {
		ch := make(chan struct{}, 1)
		w := &boundedCapture{limit: limit, overflow: ch}
		src := bytes.NewReader(bytes.Repeat([]byte{'x'}, 8192))
		n, err := io.Copy(w, src)
		if err != nil || n != 8192 || src.Len() != 0 {
			t.Fatalf("未 drain: %d %v", n, err)
		}
		if int64(len(w.data)) != limit || !w.exceeded {
			t.Fatal("保存越界或遗漏溢出")
		}
		select {
		case <-ch:
		default:
			t.Fatal("没有溢出信号")
		}
	}
	ch := make(chan struct{}, 1)
	w := &boundedCapture{limit: 0, overflow: ch}
	if _, err := w.Write(nil); err != nil || w.exceeded {
		t.Fatal("零输出被误判")
	}
}
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
