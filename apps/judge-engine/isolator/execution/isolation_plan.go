//go:build linux && amd64

package execution

import (
	"slices"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/isolator/cgroup"
	"cherry-oj/judge-engine/isolator/startup"
)

// 工作区容量不等于进程内存限额；CPU 速率不等于累计 CPU 预算。
const (
	cpuPeriod       = 10 * time.Millisecond
	workspaceBytes  = 128 << 20
	workspaceInodes = 4096
)

// isolationPlan 是已校验请求与受保护部署配置的私有快照，不拥有内核资源。
// 内部字段不作为 wire 类型，客户端不能借它选择挂载来源或身份。
type isolationPlan struct {
	request    hostexec.Request
	namespaces namespacePlan
	filesystem filesystemPlan
	resources  cgroup.Limits
	identity   identityPlan
}
type namespacePlan struct{ cloneFlags uintptr }
type filesystemPlan struct {
	rootFS, stateDir, executable string
	workspaceBytes               int64
	workspaceInodes              int
}
type identityPlan struct{ payloadUID, payloadGID, initUID, initGID int }

func newIsolationPlan(r hostexec.Request, env Environment) isolationPlan {
	return isolationPlan{
		request: cloneRequest(r), namespaces: namespacePlan{cloneFlags: isolatedNamespaces()},
		filesystem: filesystemPlan{env.RootFS, env.StateDir, env.Executable, workspaceBytes, workspaceInodes},
		resources:  cgroup.Limits{MemoryBytes: r.Limits.MemoryBytes, MaxProcesses: r.Limits.MaxProcesses, CPUQuotaNs: cpuPeriod.Nanoseconds(), CPUPeriodNs: cpuPeriod.Nanoseconds()},
		identity:   identityPlan{env.PayloadUID, env.PayloadGID, env.InitUID, env.InitGID},
	}
}
func cloneRequest(r hostexec.Request) hostexec.Request {
	r.Command = slices.Clone(r.Command)
	r.Env = slices.Clone(r.Env)
	r.Inputs = slices.Clone(r.Inputs)
	r.Outputs = slices.Clone(r.Outputs)
	return r
}
func (p isolationPlan) stage(mountpoint string) startup.StageSpec {
	return startup.StageSpec{
		Request: cloneRequest(p.request), RootFS: p.filesystem.rootFS, MountPoint: mountpoint, Executable: p.filesystem.executable,
		PayloadUID: p.identity.payloadUID, PayloadGID: p.identity.payloadGID, InitUID: p.identity.initUID, InitGID: p.identity.initGID,
		WorkspaceBytes: p.filesystem.workspaceBytes, WorkspaceInodes: p.filesystem.workspaceInodes,
	}
}
