//go:build !linux || !amd64

package launcher

import "os"

// 专用启动阶段不支持的平台直接退出；不能回退到普通 host 执行。
func RunExecStage(ExecSpec) { os.Exit(125) }
