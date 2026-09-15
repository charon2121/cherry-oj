package sandbox

import (
	"fmt"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
)

// 这几个期限分布在不同的层，却彼此有顺序要求：HTTP 写期限在服务入口，会话期限在本机执行协议，
// 墙钟硬界在协议校验里。任何一层不大于它下面那一层，表现都是「沙箱正常跑着，调用方先超时了」，
// 报出来是平台错误，查半天查不到原因。此前这些关系只写在 README 的段落里，没有任何东西检查。
const (
	httpReadHeaderTimeout = 5 * time.Second
	httpReadTimeout       = 30 * time.Second
	httpWriteTimeout      = 160 * time.Second
	httpIdleTimeout       = 30 * time.Second
	httpMaxHeaderBytes    = 16 << 10
	httpShutdownTimeout   = 10 * time.Second
)

// checkBudget 断言跨层期限的顺序。参数化是为了能直接用冲突取值验证它确实会拒绝，
// 而不是只在正确取值下跑一遍等于没测。
func checkBudget(httpWrite, session, maxWall time.Duration) error {
	if httpWrite <= session {
		return fmt.Errorf("the HTTP write deadline (%s) must be greater than the local session deadline (%s): otherwise the connection is cut first and the caller sees a transport failure instead of an execution conclusion", httpWrite, session)
	}
	if session <= maxWall {
		return fmt.Errorf("the local session deadline (%s) must be greater than the single-execution wall-clock hard limit (%s): otherwise a command that reaches its wall-clock limit is interrupted by the session deadline first, and the timeout is reported as a platform error", session, maxWall)
	}
	return nil
}

// budget 用本服务实际使用的取值做一次断言。
func budget() error {
	return checkBudget(httpWriteTimeout, hostexec.SessionTimeout, time.Duration(hostexec.MaxClockNs))
}
