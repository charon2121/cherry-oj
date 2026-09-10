// White-box checks of reserved identity validation and slot ownership.
package helper

import (
	"strings"
	"testing"
)

func TestSlotIdentityRange(t *testing.T) {
	c := Config{SocketPath: "/run/cherry/helper.sock", StateDir: "/run/cherry", JobsDir: "/sys/fs/cgroup/cherry/jobs", RootFS: "/opt/cherry/rootfs", ManifestPath: "/opt/cherry/manifest", ManifestSHA256: strings.Repeat("a", 64), ServiceUID: 61001, ServiceGID: 61001, PayloadUID: 61002, PayloadGID: 61002, InitUID: 61003, InitGID: 61003, Parallelism: 4}
	if err := c.Validate(); err != nil {
		t.Fatal(err)
	}
	for slot := 0; slot < 4; slot++ {
		p := c.forSlot(slot)
		if p.PayloadUID != 61002+2*slot || p.InitGID != 61003+2*slot || p.ServiceUID != 61001 {
			t.Fatal(p)
		}
	}
	for _, change := range []func(*Config){
		func(c *Config) { c.ServiceUID = 61004 },
		func(c *Config) { c.InitUID = 61004 },
		func(c *Config) { c.InitGID = 61006 },
		func(c *Config) { c.PayloadUID = 1<<32 - 3 },
		func(c *Config) { c.PayloadGID = 1<<32 - 3 },
	} {
		bad := c
		change(&bad)
		if bad.Validate() == nil {
			t.Fatal("accepted collision/overflow", bad)
		}
	}
	slots := availableSlots(2)
	first, second := <-slots, <-slots
	if first == second {
		t.Fatal("identity reused concurrently")
	}
	select {
	case <-slots:
		t.Fatal("busy slot reused")
	default:
	}
	slots <- first
	if got := <-slots; got != first {
		t.Fatal(got)
	}
}
