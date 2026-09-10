package helper

import "fmt"

// forSlot preserves the configured service identity while assigning a disjoint
// payload/supervisor pair. Validate checks the entire reserved range beforehand.
func (c Config) forSlot(slot int) Config {
	c.PayloadUID += 2 * slot
	c.PayloadGID += 2 * slot
	c.InitUID += 2 * slot
	c.InitGID += 2 * slot
	return c
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
					return fmt.Errorf("槽位身份重叠或越界: slot=%d id=%d", slot, id)
				}
				set.used[id] = true
			}
		}
	}
	return nil
}

// availableSlots carries identities, not reusable execution resources. A caller
// returns the slot only after serveConn has closed every owned execution/file.
func availableSlots(count int) chan int {
	slots := make(chan int, count)
	for slot := 0; slot < count; slot++ {
		slots <- slot
	}
	return slots
}
