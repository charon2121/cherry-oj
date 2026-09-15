package sandbox

import (
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
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
	session := hostexec.SessionTimeout
	wall := time.Duration(hostexec.MaxClockNs)

	err := checkBudget(session, session, wall) // HTTP 写期限不大于会话期限
	if err == nil {
		t.Fatal("HTTP 写期限小于等于会话期限却被接受")
	}
	for _, want := range []string{"HTTP 写期限", "会话期限"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("错误信息 %q 没有点明 %q", err, want)
		}
	}

	err = checkBudget(2*wall, wall, wall) // 会话期限不大于墙钟硬界
	if err == nil {
		t.Fatal("会话期限小于等于墙钟硬界却被接受")
	}
	for _, want := range []string{"会话期限", "墙钟硬界"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("错误信息 %q 没有点明 %q", err, want)
		}
	}
}
