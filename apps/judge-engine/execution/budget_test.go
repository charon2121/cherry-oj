package execution

import (
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/execution/backend"
)

// 本服务实际使用的取值必须自洽，否则启动就该失败。
func TestShippedBudgetIsConsistent(t *testing.T) {
	if err := budget(); err != nil {
		t.Fatalf("交付的期限组合本身不自洽: %v", err)
	}
	if err := DefaultConfig().Validate(); err != nil {
		t.Fatalf("默认配置被预算断言拒绝: %v", err)
	}
}

// 冲突取值必须被拒绝，并且说清楚是哪两项冲突——只说「预算无效」等于没说。
func TestConflictingBudgetIsRejected(t *testing.T) {
	executor := backend.ExecutorBound
	wall := time.Duration(backend.MaxClockNs)

	err := checkBudget(executor, executor, wall) // HTTP 写期限不大于执行器调用上界
	if err == nil {
		t.Fatal("HTTP 写期限小于等于执行器调用上界却被接受")
	}
	for _, want := range []string{"HTTP write deadline", "executor call bound"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("错误信息 %q 没有点明 %q", err, want)
		}
	}

	err = checkBudget(2*wall, wall, wall) // 执行器调用上界不大于墙钟硬界
	if err == nil {
		t.Fatal("执行器调用上界小于等于墙钟硬界却被接受")
	}
	for _, want := range []string{"executor call bound", "wall-clock hard limit"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("错误信息 %q 没有点明 %q", err, want)
		}
	}
}
