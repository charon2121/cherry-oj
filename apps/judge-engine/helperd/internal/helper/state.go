package helper

import "fmt"

type executionState uint8

const (
	executionNew executionState = iota
	executionStarting
	executionRunning
	executionFinishing
	executionFinished
	executionCleanupFailed
)

func (s executionState) String() string {
	switch s {
	case executionNew:
		return "new"
	case executionStarting:
		return "starting"
	case executionRunning:
		return "running"
	case executionFinishing:
		return "finishing"
	case executionFinished:
		return "finished"
	case executionCleanupFailed:
		return "cleanup-failed"
	}
	return fmt.Sprintf("unknown(%d)", uint8(s))
}

// executionTransitions 显式列出允许的状态转移。此前状态只在 Run / Close / finish 里零散赋值，
// 「哪些转移是合法的」要靠通读三处才能拼出来；写成表之后非法转移会直接报错，而不是碰巧没撞上。
//
//	new        → starting（取得资源组，开始启动）
//	new        → finished / cleanup-failed（从未启动就被 Close）
//	starting   → running（握手完成并放行 payload）
//	starting   → finishing（启动失败或监督提前结束，仍要走完回收）
//	running    → finishing
//	finishing  → finished / cleanup-failed
var executionTransitions = map[executionState][]executionState{
	executionNew:       {executionStarting, executionFinished, executionCleanupFailed},
	executionStarting:  {executionRunning, executionFinishing},
	executionRunning:   {executionFinishing},
	executionFinishing: {executionFinished, executionCleanupFailed},
}

func (x *execution) transition(to executionState) error {
	for _, allowed := range executionTransitions[x.state] {
		if allowed == to {
			x.state = to
			return nil
		}
	}
	return fmt.Errorf("执行状态非法转移: %s → %s", x.state, to)
}
