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
			wantErrPart:    "init exited early",
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
	x.supervision.reportLost = tc.initReportLost
	if tc.failure != nil {
		x.supervision.fail(tc.failure) // fail 同时把初步结论置为平台故障
	} else if tc.reason != "" {
		x.supervision.reason = tc.reason
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
			result, fatal := finishFrom(t, x, context.Background())
			if fatal != nil {
				t.Fatalf("回收失败: %v", fatal)
			}
			checkConclusion(t, tc, result.Reason, result.Signal, result.Error)
		})
	}
}

// finishFrom 模拟 Run 的前半段：启动已经发生，剩下的是回收。
// 直接调 finish 会被状态表拒绝（new → finishing 不是合法转移），这正是它该做的。
func finishFrom(t *testing.T, x *execution, ctx context.Context) (executionResult, error) {
	t.Helper()
	if err := x.transition(executionStarting); err != nil {
		t.Fatal(err)
	}
	return x.finish(ctx)
}

// 同一张表直接跑在纯函数上：不需要资源组、进程、FD 或任何平台能力。
// 提取之前这些分支只能通过整台执行机器间接触发，且多数要在 Linux 上才跑得到。
func TestConcludeIsPureAndTableComplete(t *testing.T) {
	for _, tc := range conclusionCases() {
		t.Run(tc.name, func(t *testing.T) {
			facts := executionFacts{
				supervision: supervisionOutcome{reason: tc.reason, reportLost: tc.initReportLost},
				group:       tc.snapshot,
				budget:      testRequest().Limits,
				completion:  processCompletion{waitErr: tc.waitErr},
			}
			if tc.failure != nil {
				facts.supervision.fail(tc.failure)
			}
			// 同一份事实连续判定两次必须得出同样的结论——纯函数不该依赖调用次序。
			first := conclude(facts)
			second := conclude(facts)
			if first.reason != second.reason || first.signal != second.signal {
				t.Fatalf("重复判定给出不同结论: %+v vs %+v", first, second)
			}
			var errText string
			if first.failure != nil {
				errText = first.failure.Error()
			}
			checkConclusion(t, tc, first.reason, first.signal, errText)
		})
	}
}

// 停组失败时计量不可信：不能拿一份读不出来的快照去推断资源结论。
func TestConcludeIgnoresAccountingWhenGroupStopFailed(t *testing.T) {
	facts := executionFacts{
		groupErr: errors.New("stop failed"),
		group:    cgroup.Snapshot{OOMKill: 2, CPUNs: 1_000_000_000},
		budget:   testRequest().Limits,
	}
	if got := conclude(facts); got.reason != "" || got.failure != nil {
		t.Fatalf("停组失败时仍用了计量: %+v", got)
	}
}

// 状态转移表拒绝非法转移，而不是碰巧没撞上。
func TestExecutionStateTransitions(t *testing.T) {
	legal := [][2]executionState{
		{executionNew, executionStarting},
		{executionNew, executionFinished},
		{executionStarting, executionRunning},
		{executionStarting, executionFinishing},
		{executionStarting, executionCleanupFailed},
		{executionRunning, executionFinishing},
		{executionFinishing, executionFinished},
		{executionFinishing, executionCleanupFailed},
	}
	for _, pair := range legal {
		x := &execution{state: pair[0]}
		if err := x.transition(pair[1]); err != nil {
			t.Errorf("合法转移被拒绝 %s → %s: %v", pair[0], pair[1], err)
		}
	}
	illegal := [][2]executionState{
		{executionNew, executionRunning},      // 没有启动就在跑
		{executionNew, executionFinishing},    // 没有可回收的执行环境
		{executionRunning, executionStarting}, // 回到启动阶段
		{executionFinished, executionFinishing},
		{executionFinished, executionStarting}, // 复用已结束的执行
		{executionCleanupFailed, executionFinished},
	}
	for _, pair := range illegal {
		x := &execution{state: pair[0]}
		if err := x.transition(pair[1]); err == nil {
			t.Errorf("非法转移被接受 %s → %s", pair[0], pair[1])
		} else if !strings.Contains(err.Error(), pair[0].String()) || !strings.Contains(err.Error(), pair[1].String()) {
			t.Errorf("错误信息没有指出是哪一步: %v", err)
		}
	}
}

// 照 probeInstallation 的断言走一遍完整的 Run：这是 helper 开放 socket 的前置条件，
// 它不通过就没有 socket，表现为「helper socket did not become ready」而看不到真正的原因。
func TestNormalRunSatisfiesStartupProbe(t *testing.T) {
	x, _, _ := scriptedExecution(processEvent{kind: processReady}, processEvent{kind: processExited, exitCode: 0})
	// 冒烟用固定限额，且要求最终计量为正数。
	x.group = nil
	x.makeGroup = func(cgroup.Limits) (executionGroup, error) {
		return &lifecycleGroup{steps: new([]string), snapshot: cgroup.Snapshot{CPUNs: 1_500_000, MemoryBytes: 4 << 20}}, nil
	}
	result, fatal := x.Run(context.Background())
	if fatal != nil {
		t.Fatalf("回收失败: %v", fatal)
	}
	if result.Reason != "" || result.ExitCode != 0 || result.Signal != 0 ||
		result.Usage.CPUNs <= 0 || result.Usage.MemoryBytes <= 0 ||
		len(result.Stdout) != 0 || len(result.Stderr) != 0 {
		t.Fatalf("启动冒烟条件不满足: reason=%q exit=%d signal=%d usage=%+v error=%q",
			result.Reason, result.ExitCode, result.Signal, result.Usage, result.Error)
	}
	if x.state != executionFinished {
		t.Fatalf("状态=%s", x.state)
	}
}

// CancelInput 的实现可以取消调用方自己的上下文——helper 的启动冒烟正是这样接线的：
// 它把 probeCancel 一并放进 cancelInput。因此「请求是否已被取消」必须在解除输入阻塞之前读取，
// 否则每次正常执行都会被判成已取消，helper 永远开不了 socket。
func TestCancelInputMustNotMakeNormalRunLookCancelled(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	x, _, _ := scriptedExecution(processEvent{kind: processReady}, processEvent{kind: processExited, exitCode: 0})
	// 与 probeInstallation 相同的接线：解除输入阻塞的同时取消本次执行的上下文。
	x.process.(*scriptedProcess).isolatedProcess.cancelInput = cancel
	x.makeGroup = func(cgroup.Limits) (executionGroup, error) {
		return &lifecycleGroup{steps: new([]string), snapshot: cgroup.Snapshot{CPUNs: 1_500_000, MemoryBytes: 4 << 20}}, nil
	}
	result, fatal := x.Run(ctx)
	if fatal != nil {
		t.Fatalf("回收失败: %v", fatal)
	}
	if result.Reason != "" || result.Cancelled {
		t.Fatalf("正常执行被判成取消: reason=%q cancelled=%v error=%q", result.Reason, result.Cancelled, result.Error)
	}
}
