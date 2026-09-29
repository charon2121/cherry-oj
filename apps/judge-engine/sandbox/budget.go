package sandbox

import (
	"fmt"
	"time"

	"cherry-oj/judge-engine/sandbox/backend"
)

// 这几个期限分布在不同的层，却彼此有顺序要求：HTTP 写期限在服务入口，执行器调用的上界由
// 执行器自己的启动、墙钟与回收期限相加得出，墙钟硬界在请求校验里。任何一层不大于它下面那一层，表现都是「沙箱正常跑着，调用方先超时了」，
// 报出来是平台错误，查半天查不到原因。此前这些关系只写在 README 的段落里，没有任何东西检查。
const (
	httpReadHeaderTimeout = 5 * time.Second
	httpReadTimeout       = 30 * time.Second
	httpWriteTimeout      = 160 * time.Second
	httpIdleTimeout       = 30 * time.Second
	httpMaxHeaderBytes    = 16 << 10
	httpShutdownTimeout   = 10 * time.Second

	// httpHeadroom 是执行池能接纳的请求（并发数 + 队列长度）之外，HTTP 层额外放行的并发名额，
	// 使执行池占满时 /blobs 上传、下载、删除和 /version 仍能进来，而不是一律 503。
	httpHeadroom = 4
)

// checkBudget 断言跨层期限的顺序。参数化是为了能直接用冲突取值验证它确实会拒绝，
// 而不是只在正确取值下跑一遍等于没测。
func checkBudget(httpWrite, executor, maxWall time.Duration) error {
	if httpWrite <= executor {
		return fmt.Errorf("the HTTP write deadline (%s) must be greater than the executor call bound (%s): otherwise the connection is cut first and the caller sees a transport failure instead of an execution conclusion", httpWrite, executor)
	}
	if executor <= maxWall {
		return fmt.Errorf("the executor call bound (%s) must be greater than the single-execution wall-clock hard limit (%s): otherwise a command that reaches its wall-clock limit has no time left for startup and reclaim, and the timeout is reported as a platform error", executor, maxWall)
	}
	return nil
}

// budget 用本服务实际使用的取值做一次断言。
func budget() error {
	return checkBudget(httpWriteTimeout, backend.ExecutorBound, time.Duration(backend.MaxClockNs))
}
