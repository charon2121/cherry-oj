package probe

// 这里规定「一个合格的原生部署长什么样」。它属于代码而不是被校验的那份清单——
// 让清单自己声明有哪些项，等于让被检查者决定检查范围：少声明一项就等于少检查一项。
// 清单只负责给出各项的期望值，代码负责核对这些项确实存在、确实被设了界限。
const (
	// releaseBinDir 是服务实际启动的符号链接所在目录。
	releaseBinDir = "/var/lib/cherry-sandbox/current/bin"
)

// requiredFiles 是部署必须声明并逐一核对摘要的文件。
var requiredFiles = []string{
	"sandbox", "helper", "rootfsManifest", "toolchainLock",
	"helperConfig", "sandboxConfig", "slice",
	"helperUnit", "sandboxUnit", "judgeUnit", "bootstrap",
}

// requiredGroups 是必须有资源上界的 cgroup 节点，requiredLimits 是每个节点必须设的控制文件。
// 两者相乘就是必须核对的条目集合；此前这里用「数量等于 24」代替，数字本身说明不了任何事，
// 多一项少一项还会互相抵消。
var requiredGroups = []string{
	"cherry.slice/cherry-sandbox.slice",
	"cherry.slice/cherry-sandbox.slice/cherry-sandbox-helper.service",
	"cherry.slice/cherry-sandbox.slice/cherry-sandbox.service",
	"cherry.slice/cherry-sandbox.slice/cherry-sandbox-judge.service",
	"cherry.slice/cherry-sandbox.slice/cherry-sandbox-helper.service/supervisor",
	"cherry.slice/cherry-sandbox.slice/cherry-sandbox-helper.service/jobs",
}

var requiredLimits = []string{"cpu.max", "memory.max", "memory.swap.max", "pids.max"}
