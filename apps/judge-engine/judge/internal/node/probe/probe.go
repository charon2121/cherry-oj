package probe

import (
	"context"
	"fmt"
	"strings"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/config"
	"cherry-oj/judge-engine/judge/internal/node/identity"
	"cherry-oj/judge-engine/judge/internal/node/wire"
)

// Sandbox 是环境探测所消费的能力。接口由消费方定义，实现是 judge 已有的 sandbox 客户端——
// 探测不再自建第三个 HTTP 客户端，超时、trace 传播与错误正文截断都沿用同一套。
type Sandbox interface {
	Version(ctx context.Context) (contract.SandboxVersion, error)
	Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error)
}

// Environment 通过 sandbox 已有的有界接口读取执行环境。探测程序中不插入任何用户输入。
func Environment(ctx context.Context, j config.Settings, sandbox Sandbox) (identity.Environment, error) {
	version, err := sandbox.Version(ctx)
	if err != nil {
		return identity.Environment{}, fmt.Errorf("sandbox environment probe unavailable: %w", err)
	}
	if version.Name != "cherry-oj-sandbox" || version.Version == "" || len(version.Version) > 128 {
		return identity.Environment{}, fmt.Errorf("sandbox version invalid")
	}
	if version.Isolation == "linux" || j.Node.DeploymentManifest != "" {
		if version.Isolation != "linux" || j.Node.DeploymentManifest == "" {
			return identity.Environment{}, fmt.Errorf("Linux sandbox requires matching deployment manifest and isolation")
		}
		return probeDeployment(ctx, j, version.Version, sandbox)
	}
	spec := contract.RunSpec{Command: []string{"/usr/bin/python3", "-c", environmentProbe}, Limits: contract.Limits{CPUNs: 2_000_000_000, ClockNs: 5_000_000_000, MemoryBytes: 134217728, MaxProcesses: 8, StdoutMaxBytes: 8192, StderrMaxBytes: 1024}}
	result, err := sandbox.Run(ctx, spec)
	if err != nil {
		return identity.Environment{}, fmt.Errorf("sandbox environment probe unavailable: %w", err)
	}
	if result.Status != contract.StatusOK || result.ExitCode != 0 {
		return identity.Environment{}, fmt.Errorf("sandbox environment probe failed")
	}
	var m struct{ Architecture, CPUModel, OSVersion, KernelVersion, ToolchainVersion, RuntimeDigest string }
	if err := wire.Decode(strings.NewReader(result.Stdout), &m); err != nil {
		return identity.Environment{}, fmt.Errorf("sandbox environment metadata invalid")
	}
	for _, field := range []struct {
		value string
		limit int
	}{{m.Architecture, 32}, {m.CPUModel, 256}, {m.OSVersion, 128}, {m.KernelVersion, 128}, {m.ToolchainVersion, 128}, {m.RuntimeDigest, 128}} {
		if field.value == "" || len(field.value) > field.limit {
			return identity.Environment{}, fmt.Errorf("sandbox environment metadata missing or too long")
		}
	}
	return identity.Environment{
		Architecture:     m.Architecture,
		CPUModel:         m.CPUModel,
		OSVersion:        m.OSVersion,
		KernelVersion:    m.KernelVersion,
		SandboxVersion:   version.Version,
		ToolchainVersion: m.ToolchainVersion,
		RuntimeDigest:    m.RuntimeDigest,
	}, nil
}

// CPU features omit volatile per-core MHz/counters. Runtime digest covers actual
// sandbox binary and container quotas, which also affect calibration reuse.
const environmentProbe = `import hashlib,json,pathlib,platform,subprocess
cpu=pathlib.Path('/proc/cpuinfo').read_text()
keys={'model name','CPU implementer','CPU architecture','CPU variant','CPU part','CPU revision','Hardware','Features','flags'}
facts=sorted({line.strip() for line in cpu.splitlines() if ':' in line and line.split(':',1)[0].strip() in keys})
if not facts: raise RuntimeError('CPU identity unavailable')
digest=lambda b:hashlib.sha256(b).hexdigest()
cpu_model=next((s.split(':',1)[1].strip() for s in facts if s.startswith('model name')),platform.machine())
osinfo=platform.freedesktop_os_release()
runtime={}
for name in ('cpu.max','memory.max','cpuset.cpus.effective'):
 p=pathlib.Path('/sys/fs/cgroup')/name
 runtime[name]=p.read_text().strip() if p.exists() else 'unavailable'
runtime['binary']=digest(pathlib.Path('/usr/local/bin/sandbox').read_bytes())
print(json.dumps(dict(Architecture=platform.machine(),CPUModel=cpu_model[:160]+' sha256:'+digest('\n'.join(facts).encode()),OSVersion=osinfo['PRETTY_NAME'],KernelVersion=platform.release(),ToolchainVersion=subprocess.check_output(['/usr/bin/g++','--version'],timeout=2).decode().splitlines()[0],RuntimeDigest=digest(json.dumps(runtime,sort_keys=True).encode()))))
`
