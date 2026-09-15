package cgroup

import (
	"fmt"
	"math"
	"strconv"
	"strings"
)

func parseFields(data []byte) (map[string]uint64, error) {
	fields := make(map[string]uint64)
	for _, line := range strings.Split(strings.TrimSpace(string(data)), "\n") {
		pair := strings.Fields(line)
		if len(pair) != 2 {
			return nil, fmt.Errorf("invalid cgroup statistics line %q", line)
		}
		if _, ok := fields[pair[0]]; ok {
			return nil, fmt.Errorf("duplicate cgroup statistics entry %q", pair[0])
		}
		value, err := strconv.ParseUint(pair[1], 10, 64)
		if err != nil {
			return nil, fmt.Errorf("invalid %s measurement: %w", pair[0], err)
		}
		fields[pair[0]] = value
	}
	return fields, nil
}

// 缺项必须是错误；零是合法的资源事实，不能用 map 零值掩盖内核接口缺失。
func required(fields map[string]uint64, name string) (uint64, error) {
	value, ok := fields[name]
	if !ok {
		return 0, fmt.Errorf("missing cgroup statistics entry %s", name)
	}
	return value, nil
}

func parseSnapshot(cpu, peak, memory, pids, events []byte) (Snapshot, error) {
	var s Snapshot
	c, err := parseFields(cpu)
	if err != nil {
		return s, err
	}
	usage, err := required(c, "usage_usec")
	if err != nil {
		return s, err
	}
	if usage > math.MaxInt64/1000 {
		return s, fmt.Errorf("CPU microsecond to nanosecond conversion overflowed")
	}
	s.CPUNs = int64(usage) * 1000
	s.MemoryBytes, err = strconv.ParseInt(strings.TrimSpace(string(peak)), 10, 64)
	if err != nil || s.MemoryBytes < 0 {
		return Snapshot{}, fmt.Errorf("invalid memory.peak %q", peak)
	}
	m, err := parseFields(memory)
	if err != nil {
		return Snapshot{}, err
	}
	if s.OOM, err = required(m, "oom"); err != nil {
		return Snapshot{}, err
	}
	if s.OOMKill, err = required(m, "oom_kill"); err != nil {
		return Snapshot{}, err
	}
	if s.MemoryMaxEvents, err = required(m, "max"); err != nil {
		return Snapshot{}, err
	}
	p, err := parseFields(pids)
	if err != nil {
		return Snapshot{}, err
	}
	if s.PidsMaxEvents, err = required(p, "max"); err != nil {
		return Snapshot{}, err
	}
	e, err := parseFields(events)
	if err != nil {
		return Snapshot{}, err
	}
	populated, err := required(e, "populated")
	if err != nil {
		return Snapshot{}, err
	}
	if populated > 1 {
		return Snapshot{}, fmt.Errorf("invalid populated: %d", populated)
	}
	s.Populated = populated == 1
	return s, nil
}
