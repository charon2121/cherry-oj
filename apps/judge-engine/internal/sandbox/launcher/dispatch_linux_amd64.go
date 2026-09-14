package launcher

import (
	"errors"
	"os"
)

// Dispatch 必须在 main 读取配置、启动服务或建立 goroutine 之前调用。
// 隐藏模式没有 setuid 位，只接收已继承的匿名 FD；请求不能选择它。
func Dispatch() bool {
	if len(os.Args) != 2 {
		return false
	}
	switch os.Args[1] {
	case "--isolated-init":
		runInit()
		os.Exit(launcherFailureExitCode)
	case "--isolated-exec":
		config := os.NewFile(ExecConfigFD, "exec-config")
		var s ExecSpec
		err := ReadFrame(config, &s, MaxFrameBytes)
		if errors.Join(err, config.Close()) != nil {
			os.Exit(launcherFailureExitCode)
		}
		RunExecStage(s)
		os.Exit(launcherFailureExitCode)
	default:
		return false
	}
	return true
}
