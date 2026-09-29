package runner

import (
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/sandbox/backend"
)

// 每个终止原因在这里登记一次结论，外加一个让该结论可区分的执行状态。
// 只断言「原因 → 状态」不够：ReasonPlatform 与兜底分支都给出 InternalError，
// 少了 OOMKilled 这个区分状态，删掉它的分支测试也不会失败。
var reasonConclusions = map[backend.Reason]struct {
	usage backend.Facts
	want  contract.Status
}{
	backend.ReasonCPU: {
		backend.Facts{Reason: backend.ReasonCPU},
		contract.StatusTimeLimitExceeded,
	},
	backend.ReasonWall: {
		backend.Facts{Reason: backend.ReasonWall},
		contract.StatusTimeLimitExceeded,
	},
	backend.ReasonOutput: {
		backend.Facts{Reason: backend.ReasonOutput},
		contract.StatusOutputLimitExceeded,
	},
	// 平台故障优先于内存结论：OOM 证据存在时仍然是 InternalError，不是 MLE。
	backend.ReasonPlatform: {
		backend.Facts{Reason: backend.ReasonPlatform, OOMKilled: true},
		contract.StatusInternalError,
	},
	// 取消没有专用分支，落在「原因非空即平台错误」上。这是刻意的结论，不是遗漏：
	// 取消后拿到的执行事实不足以支持任何资源判定，往严格方向倒。
	backend.ReasonCancelled: {
		backend.Facts{Reason: backend.ReasonCancelled},
		contract.StatusInternalError,
	},
}

// 新增 Reason 而不在上表登记时，本测试失败：未知原因会静默落进兜底分支，
// 而兜底给出的是平台错误——把一次真实的资源超限报成 SE，查不出原因。
func TestEveryReasonHasAConclusion(t *testing.T) {
	for _, reason := range backend.AllReasons() {
		conclusion, ok := reasonConclusions[reason]
		if !ok {
			t.Errorf("终止原因 %q 没有登记结论：请在 reasonConclusions 中显式决定它映射到哪个状态", reason)
			continue
		}
		t.Run(string(reason), func(t *testing.T) {
			if got := classify(defaultLimits(), conclusion.usage, false, nil); got != conclusion.want {
				t.Fatalf("classify(%q) = %s，期望 %s", reason, got, conclusion.want)
			}
		})
	}
}

// 反向约束：上表不能登记协议里不存在的原因，否则删掉一个取值后表里会留下死条目。
func TestReasonConclusionsHaveNoStaleEntries(t *testing.T) {
	declared := map[backend.Reason]bool{}
	for _, reason := range backend.AllReasons() {
		declared[reason] = true
	}
	for reason := range reasonConclusions {
		if !declared[reason] {
			t.Errorf("reasonConclusions 登记了协议中不存在的终止原因 %q", reason)
		}
	}
}
