package preflight

// 这里规定「一个合格的原生部署长什么样」。它属于代码而不是被校验的那份清单——
// 让清单自己声明有哪些项，等于让被检查者决定检查范围：少声明一项就等于少检查一项。
// 清单只负责给出各项的期望值，代码负责核对这些项确实存在、确实被设了界限。
const (
	// releaseDir 是服务实际启动时经过的符号链接：current → releases/<版本>。
	releaseDir = "/var/lib/cherry-sandbox/current"
)

// releaseFiles 是部署清单中必须与 current 下实际启动路径一致的文件：清单校验的那一份，
// 必须正是服务运行的那一份。
var releaseFiles = map[string]string{"sandbox": "bin/sandbox", "executor": "libexec/sandbox"}

// requiredFiles 是部署必须声明并逐一核对摘要的文件。
var requiredFiles = []string{
	"sandbox", "executor", "rootfsManifest", "toolchainLock",
	"executorConfig", "sandboxConfig", "startConfig", "slice",
	"sandboxUnit", "judgeUnit", "bootstrap",
}

// requiredGroups 是必须有资源上界的 cgroup 节点，requiredLimits 是每个节点必须设的控制文件。
// 两者相乘就是必须核对的条目集合；此前这里用「数量等于 24」代替，数字本身说明不了任何事，
// 多一项少一项还会互相抵消。
var requiredGroups = []string{
	"cherry.slice/cherry-sandbox.slice",
	"cherry.slice/cherry-sandbox.slice/cherry-sandbox.service",
	"cherry.slice/cherry-sandbox.slice/cherry-sandbox-judge.service",
	"cherry.slice/cherry-sandbox.slice/cherry-sandbox.service/supervisor",
	"cherry.slice/cherry-sandbox.slice/cherry-sandbox.service/jobs",
}

var requiredLimits = []string{"cpu.max", "memory.max", "memory.swap.max", "pids.max"}
