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
			return nil, fmt.Errorf("无效 cgroup 统计行 %q", line)
		}
		if _, ok := fields[pair[0]]; ok {
			return nil, fmt.Errorf("重复 cgroup 统计项 %q", pair[0])
		}
		value, err := strconv.ParseUint(pair[1], 10, 64)
		if err != nil {
			return nil, fmt.Errorf("无效 %s 计量: %w", pair[0], err)
		}
		fields[pair[0]] = value
	}
	return fields, nil
}

func required(fields map[string]uint64, name string) (uint64, error) {
	value, ok := fields[name]
	if !ok {
		return 0, fmt.Errorf("缺少 cgroup 统计项 %s", name)
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
		return s, fmt.Errorf("CPU 微秒换纳秒溢出")
	}
	s.CPUNs = int64(usage) * 1000
	s.MemoryBytes, err = strconv.ParseInt(strings.TrimSpace(string(peak)), 10, 64)
	if err != nil || s.MemoryBytes < 0 {
		return Snapshot{}, fmt.Errorf("无效 memory.peak %q", peak)
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
		return Snapshot{}, fmt.Errorf("无效 populated: %d", populated)
	}
	s.Populated = populated == 1
	return s, nil
}
