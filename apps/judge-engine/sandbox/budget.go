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
		return fmt.Errorf("HTTP 写期限（%s）必须大于本机会话期限（%s）："+
			"否则连接会先被切断，调用方看到的是传输失败而不是执行结论", httpWrite, session)
	}
	if session <= maxWall {
		return fmt.Errorf("本机会话期限（%s）必须大于单次执行墙钟硬界（%s）："+
			"否则达到墙钟上限的命令会先被会话期限打断，超时被报成平台错误", session, maxWall)
	}
	return nil
}

// budget 用本服务实际使用的取值做一次断言。
func budget() error {
	return checkBudget(httpWriteTimeout, hostexec.SessionTimeout, time.Duration(hostexec.MaxClockNs))
}
