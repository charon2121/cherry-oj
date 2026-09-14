package helper

import (
	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/internal/sandbox/cgroup"
)

// usageOf 把 cgroup 快照转成线格式。两个类型字段相同却有意分开：cgroup.Snapshot 属于
// 特权侧的资源计量实现，hostexec.Usage 是协议的一部分。合并会让非特权客户端依赖
// cgroup 包，也会让 cgroup 的实现细节变更直接改动线格式。
func usageOf(s cgroup.Snapshot) hostexec.Usage {
	return hostexec.Usage{
		CPUNs:           s.CPUNs,
		MemoryBytes:     s.MemoryBytes,
		OOM:             s.OOM,
		OOMKill:         s.OOMKill,
		MemoryMaxEvents: s.MemoryMaxEvents,
		PidsMaxEvents:   s.PidsMaxEvents,
		Populated:       s.Populated,
	}
}
