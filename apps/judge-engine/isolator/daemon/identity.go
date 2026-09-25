//go:build linux && amd64

package daemon

import (
	"cherry-oj/judge-engine/isolator/execution"
	"fmt"
)

// forSlot 每槽保留两个身份位置，使并发任务的 payload/init 不共享 UID/GID。
// 共享身份会扩大信号等同身份操作的范围；Validate 在接单前核对整段身份无重叠。
func (c Config) forSlot(slot int) Config {
	c.PayloadUID += 2 * slot
	c.PayloadGID += 2 * slot
	c.InitUID += 2 * slot
	c.InitGID += 2 * slot
	return c
}

// environment 是本槽位交给 execution 的固定环境；调用前应已经过 forSlot。
func (c Config) environment(executable string) execution.Environment {
	return execution.Environment{RootFS: c.RootFS, StateDir: c.StateDir, Executable: executable,
		PayloadUID: c.PayloadUID, PayloadGID: c.PayloadGID, InitUID: c.InitUID, InitGID: c.InitGID}
}

func (c Config) validateSlotIdentities() error {
	uids := map[int]bool{c.ServiceUID: true}
	gids := map[int]bool{c.ServiceGID: true}
	for slot := 0; slot < c.Parallelism; slot++ {
		pair := c.forSlot(slot)
		for _, set := range []struct {
			ids  []int
			used map[int]bool
		}{
			{[]int{pair.PayloadUID, pair.InitUID}, uids},
			{[]int{pair.PayloadGID, pair.InitGID}, gids},
		} {
			for _, id := range set.ids {
				if id <= 0 || uint64(id) >= 1<<32-1 || set.used[id] {
					return fmt.Errorf("slot identities overlap or are out of range: slot=%d id=%d", slot, id)
				}
				set.used[id] = true
			}
		}
	}
	return nil
}

// availableSlots 只分配身份槽位，不复用 cgroup 或工作区。
// serveInSlot 等执行和交付收尾后归还槽位，避免下一次执行复用仍被占用的身份。
func availableSlots(count int) chan int {
	slots := make(chan int, count)
	for slot := 0; slot < count; slot++ {
		slots <- slot
	}
	return slots
}
