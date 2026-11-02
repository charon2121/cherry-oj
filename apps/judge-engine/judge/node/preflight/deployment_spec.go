package preflight

import "regexp"

// 这里规定「一个合格的原生部署长什么样」。它属于代码而不是被校验的那份清单——
// 让清单自己声明有哪些项，等于让被检查者决定检查范围：少声明一项就等于少检查一项。
// 清单只负责给出各文件的期望摘要，代码负责核对这些文件确实存在、摘要一致。
const (
	// releaseDir 是服务实际启动时经过的符号链接：current → releases/<版本>。
	releaseDir = "/opt/cherry-oj/current"
)

// releaseFiles 是部署清单中必须与 current 下实际启动路径一致的文件：清单校验的那一份，
// 必须正是服务运行的那一份。每门语言的工具链文件同样如此，见 toolchainReleaseFile。
var releaseFiles = map[string]string{"executor": "libexec/sandbox"}

// requiredFiles 是部署必须声明并逐一核对摘要的固定文件。
var requiredFiles = []string{
	"executor", "executorConfig", "startConfig", "slice",
	"judgeUnit", "bootstrap",
}

// 除固定文件外，部署清单只能再登记工具链：每门语言恰好两项，toolchain.<语言>.manifest（rootfs 逐文件清单）
// 与 toolchain.<语言>.lock（软件包锁），至少一门（docs/protocols/install-layout.md）。
var toolchainKey = regexp.MustCompile(`^toolchain\.([a-z][a-z0-9-]{0,31})\.(manifest|lock)$`)

// toolchainReleaseFile 是工具链文件在发布里的相对路径。
func toolchainReleaseFile(language, kind string) string {
	if kind == "manifest" {
		return "toolchains/" + language + "/manifest.json"
	}
	return "toolchains/" + language + "/packages.lock.json"
}
