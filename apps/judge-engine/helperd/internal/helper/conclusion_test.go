package helper

import (
	"context"
	"errors"
	"io"
	"os/exec"
	"strings"
	"syscall"
	"testing"

	"cherry-oj/judge-engine/helperd/internal/cgroup"
	"cherry-oj/judge-engine/internal/hostexec"
)

// 这张表是「执行事实 → 终止结论」的真源。
//
// 它先于重构写成，跑在重构前的实现上，再原样跑在提取出的纯函数上：任何行为漂移都会表现为
// 这里失败，而不是靠人读 diff 发现。判断标准只有一条——同样的事实必须得出同样的结论。
type conclusionCase struct {
	name string
	// 监督阶段已经得出的初步结论
	reason  hostexec.Reason
	failure error
	// 停组后的最终计量与等待事实
	snapshot       cgroup.Snapshot
	initReportLost bool
	waitErr        error

	wantReason  hostexec.Reason
	wantSignal  int
	wantErrPart string // 期望出现在结论错误中的片段；空表示不应有错误
}

func conclusionCases() []conclusionCase {
	return []conclusionCase{
		{
			name:       "正常退出",
			wantReason: "",
		},
		{
			name:       "监督已判定墙钟超时",
			reason:     hostexec.ReasonWall,
			wantReason: hostexec.ReasonWall,
		},
		{
			name:       "监督已判定取消",
			reason:     hostexec.ReasonCancelled,
			wantReason: hostexec.ReasonCancelled,
		},
		{
			// 累计 CPU 由最终计量兜底：采样可能恰好错过最后一段。
			name:       "最终计量显示 CPU 用尽",
			snapshot:   cgroup.Snapshot{CPUNs: 1_000_000_000},
			wantReason: hostexec.ReasonCPU,
		},
		{
			// 已有结论的不被最终计量覆盖：先发生的原因更接近真相。
			name:       "已判定墙钟时最终计量不改判 CPU",
			reason:     hostexec.ReasonWall,
			snapshot:   cgroup.Snapshot{CPUNs: 1_000_000_000},
			wantReason: hostexec.ReasonWall,
		},
		{
			// oom_kill 只说明本组有受害进程，可能来自祖先或全局 OOM；
			// 缺少本任务 oom 证据时，不能把平台内存压力归为用户命令超限。
			name:        "祖先 OOM 没有本任务证据",
			snapshot:    cgroup.Snapshot{OOMKill: 2},
			wantReason:  hostexec.ReasonPlatform,
			wantErrPart: "without task-local OOM",
		},
		{
			name:           "祖先 OOM 同时丢失退出报告仍是平台故障",
			snapshot:       cgroup.Snapshot{OOMKill: 2},
			initReportLost: true,
			failure:        io.EOF,
			reason:         hostexec.ReasonPlatform,
			wantReason:     hostexec.ReasonPlatform,
			wantErrPart:    "without task-local OOM",
		},
		{
			// 本任务 OOM 可能连 init 一起杀死；最终计量可以解释丢失的退出报告。
			name:           "本任务 OOM 解释了丢失的退出报告",
			snapshot:       cgroup.Snapshot{OOM: 1, OOMKill: 1},
			initReportLost: true,
			failure:        io.EOF,
			reason:         hostexec.ReasonPlatform,
			wantReason:     "",
			wantSignal:     int(syscall.SIGKILL),
		},
		{
			// 没有丢失退出报告时，独立的失败不能被 OOM 证据抹掉。
			name:        "本任务 OOM 不掩盖独立故障",
			snapshot:    cgroup.Snapshot{OOM: 1, OOMKill: 1},
			failure:     errors.New("snapshot failed"),
			reason:      hostexec.ReasonPlatform,
			wantReason:  hostexec.ReasonPlatform,
			wantErrPart: "snapshot failed",
		},
		{
			// 既没有 OOM 证据又丢了退出报告，init 提前退出就是平台故障。
			name:           "丢失退出报告且无 OOM 证据",
			initReportLost: true,
			waitErr:        errors.New("init exited"),
			wantReason:     hostexec.ReasonPlatform,
			wantErrPart:    "init 提前退出",
		},
	}
}

// injectInitExit 让 Wait 观察到 init 的退出事实，用来构造「丢失退出报告」那组事实。
func injectInitExit(t *testing.T, x *execution) {
	t.Helper()
	err := exec.Command("sh", "-c", "exit 1").Run()
	var exit *exec.ExitError
	if !errors.As(err, &exit) {
		t.Fatal(err)
	}
	p := x.process.(*isolatedProcess)
	p.initStopped = false
	p.initExited = make(chan error, 1)
	p.initExited <- err
}

func applyFacts(t *testing.T, x *execution, tc conclusionCase) {
	t.Helper()
	var steps []string
	x.group = &lifecycleGroup{steps: &steps, snapshot: tc.snapshot}
	x.initReportLost = tc.initReportLost
	if tc.failure != nil {
		x.fail(tc.failure) // fail 同时把结论置为平台故障
	} else if tc.reason != "" {
		x.result.Reason = tc.reason
	}
	if tc.waitErr != nil {
		injectInitExit(t, x)
	}
}

func checkConclusion(t *testing.T, tc conclusionCase, reason hostexec.Reason, signal int, errText string) {
	t.Helper()
	if reason != tc.wantReason {
		t.Errorf("reason=%q want %q", reason, tc.wantReason)
	}
	if signal != tc.wantSignal {
		t.Errorf("signal=%d want %d", signal, tc.wantSignal)
	}
	if tc.wantErrPart == "" {
		if errText != "" {
			t.Errorf("不应有结论错误，得到 %q", errText)
		}
	} else if !strings.Contains(errText, tc.wantErrPart) {
		t.Errorf("结论错误 %q 中没有 %q", errText, tc.wantErrPart)
	}
}

// 表驱动断言跑在完整的 finish 上：这是重构前后共用的对照基准。
func TestConclusionFromFacts(t *testing.T) {
	for _, tc := range conclusionCases() {
		t.Run(tc.name, func(t *testing.T) {
			x := newTestExecution(testRequest(), func() {})
			applyFacts(t, x, tc)
			result, fatal := x.finish(context.Background())
			if fatal != nil {
				t.Fatalf("回收失败: %v", fatal)
			}
			checkConclusion(t, tc, result.Reason, result.Signal, result.Error)
		})
	}
}
