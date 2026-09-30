package backend

import (
	"regexp"
	"strings"
	"time"
)

// 这些取值与 C 执行器（apps/sandbox/src/sandbox.h）的硬界一一对应。执行器自己也会校验，
// 这里提前拒绝，是为了在写 box 之前就给出明确的错误，而不是让一次注定被拒的请求落盘。
const (
	// MaxInputBytes 是输入文件与 stdin 的合计上限。
	MaxInputBytes int64 = 64 << 20
	// MaxArtifactBytes 是一次执行交付的产物合计上限。
	MaxArtifactBytes int64 = 64 << 20
	// MaxClockNs 是单次执行的墙钟硬界，也是跨层期限预算的下界。
	MaxClockNs = int64(120 * time.Second)

	maxPathSegments = 8
)

var segment = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$`)

// ValidPath 只接受工作区相对名称：最多 8 段，排除空段、点段和以点开头的内部文件。
// 它不能替代执行器打开文件时的链接与挂载边界校验。
func ValidPath(name string) bool {
	parts := strings.Split(name, "/")
	if len(parts) > maxPathSegments {
		return false
	}
	for _, p := range parts {
		if !segment.MatchString(p) {
			return false
		}
	}
	return true
}

// Reason 是主动终止或平台故障原因，不替代退出信号和 cgroup 资源事实。
// 空值表示没有发生主动终止，由退出码与信号说明结果。取值即执行器输出的线格式。
type Reason string

const (
	ReasonCPU       Reason = "cpu"
	ReasonWall      Reason = "wall"
	ReasonOutput    Reason = "output"
	ReasonCancelled Reason = "cancelled"
	ReasonPlatform  Reason = "platform"
)

// AllReasons 列出全部非空取值。消费方的结论映射必须覆盖其中每一项，由测试断言；
// 新增取值而不更新映射时测试失败，避免新原因悄悄落进兜底分支。
func AllReasons() []Reason {
	return []Reason{ReasonCPU, ReasonWall, ReasonOutput, ReasonCancelled, ReasonPlatform}
}
