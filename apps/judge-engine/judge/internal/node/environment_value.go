package node

import "cherry-oj/judge-engine/judge/internal/config"

// Environment 是本次进程实际所处的执行环境，与配置刻意分开：
// 配置是人写下的意图，环境是探测出来的事实。混在一起之后，读代码的人无法判断
// 某个字段此刻来自哪一种来源——这正是重构前 config.Settings 被回填造成的问题。
//
// 节点链路未启用时取配置中的声明值；启用时由 Probe 的实测结果整体替换。
type Environment struct {
	Architecture     string
	CPUModel         string
	OSVersion        string
	KernelVersion    string
	SandboxVersion   string
	ToolchainVersion string
	RuntimeDigest    string
}

// DeclaredEnvironment 取配置中声明的环境，供未启用节点链路时使用。
// 它不含实测成分，因此不带 Architecture 与 RuntimeDigest——这两项只能测出来。
func DeclaredEnvironment(s config.Settings) Environment {
	return Environment{
		CPUModel:         s.Node.CPUModel,
		OSVersion:        s.Node.OSVersion,
		KernelVersion:    s.Node.KernelVersion,
		SandboxVersion:   s.Node.SandboxVersion,
		ToolchainVersion: s.Node.ToolchainVersion,
	}
}
