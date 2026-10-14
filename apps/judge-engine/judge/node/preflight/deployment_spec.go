package preflight

// 这里规定「一个合格的原生部署长什么样」。它属于代码而不是被校验的那份清单——
// 让清单自己声明有哪些项，等于让被检查者决定检查范围：少声明一项就等于少检查一项。
// 清单只负责给出各文件的期望摘要，代码负责核对这些文件确实存在、摘要一致。
const (
	// releaseDir 是服务实际启动时经过的符号链接：current → releases/<版本>。
	releaseDir = "/opt/cherry-oj/current"
)

// releaseFiles 是部署清单中必须与 current 下实际启动路径一致的文件：清单校验的那一份，
// 必须正是服务运行的那一份。
var releaseFiles = map[string]string{"executor": "libexec/sandbox"}

// requiredFiles 是部署必须声明并逐一核对摘要的文件。
var requiredFiles = []string{
	"executor", "rootfsManifest", "toolchainLock",
	"executorConfig", "startConfig", "slice",
	"judgeUnit", "bootstrap",
}
