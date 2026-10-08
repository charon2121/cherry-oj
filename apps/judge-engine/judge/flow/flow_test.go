package flow_test

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"math"
	"os"
	"path/filepath"
	"reflect"
	"slices"
	"strings"
	"testing"
	"unicode/utf8"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/flow"
)

type runReply struct {
	result contract.RunResult
	err    error
}

type deletion struct {
	ref    string
	ctxErr error
}

type fakeSandbox struct {
	runs         []runReply
	calls        []contract.RunSpec
	uploaded     [][]byte
	deleted      []deletion
	uploadErrors map[int]error // key 是从 1 开始的第几次 Upload
	deleteErr    error
	onRun        func(call int)
}

func (f *fakeSandbox) Upload(ctx context.Context, body io.Reader) (string, error) {
	b, err := io.ReadAll(body)
	if err != nil {
		return "", err
	}
	f.uploaded = append(f.uploaded, b)
	call := len(f.uploaded)
	if err := f.uploadErrors[call]; err != nil {
		return "", err
	}
	return fmt.Sprintf("ref-%d", call), nil
}

func (f *fakeSandbox) Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error) {
	f.calls = append(f.calls, spec)
	call := len(f.calls)
	if f.onRun != nil {
		f.onRun(call)
	}
	if call > len(f.runs) {
		return contract.RunResult{}, fmt.Errorf("unexpected Run call %d", call)
	}
	reply := f.runs[call-1]
	return reply.result, reply.err
}

func (f *fakeSandbox) Delete(ctx context.Context, ref string) error {
	f.deleted = append(f.deleted, deletion{ref: ref, ctxErr: ctx.Err()})
	return f.deleteErr
}

func judgeConfig() config.Settings {
	return config.Default().Judge
}

func trialRequest(language string, cases ...contract.CaseSpec) contract.JudgeRequest {
	return contract.JudgeRequest{
		SubmissionID: "submission-1",
		ProblemID:    "problem-1",
		LanguageID:   language,
		Source:       "source code",
		Limits: contract.JudgeLimits{
			CPUNs:       1_000,
			MemoryBytes: 2_000,
		},
		Mode:  contract.ModeTrial,
		Cases: cases,
	}
}

func oneCaseRequest(language string) contract.JudgeRequest {
	return trialRequest(language, contract.CaseSpec{Input: "input\n", Expected: "answer\n", Name: "sample"})
}

func compileOK() runReply {
	return runReply{result: contract.RunResult{
		Status:    contract.StatusOK,
		Artifacts: map[string]string{"Main": "executable-ref"},
	}}
}

func runOK(stdout string) runReply {
	return runReply{result: contract.RunResult{Status: contract.StatusOK, Stdout: stdout}}
}

func TestJudgeCompiledACBuildsExpectedSpecs(t *testing.T) {
	cfg := judgeConfig()
	req := trialRequest("cpp",
		contract.CaseSpec{Input: "1 2\n", Expected: "3\n", Name: "first"},
		contract.CaseSpec{Input: "3 4\n", Expected: "7\n", Name: "second"},
	)
	fake := &fakeSandbox{runs: []runReply{
		compileOK(),
		{result: contract.RunResult{Status: contract.StatusOK, Stdout: "3\n", CPUNs: 11, MemoryBytes: 40}},
		{result: contract.RunResult{Status: contract.StatusOK, Stdout: "7\n", CPUNs: 22, MemoryBytes: 30}},
	}}

	result := flow.Judge(context.Background(), fake, cfg, req, nil)

	if result.Verdict != contract.VerdictAC || result.Score != 100 {
		t.Fatalf("result = %+v", result)
	}
	if len(result.CaseResults) != 2 || result.CaseResults[0].Name != "first" || result.CaseResults[1].Name != "second" {
		t.Fatalf("cases = %+v", result.CaseResults)
	}
	if result.CPUNs != 22 || result.MemoryBytes != 40 {
		t.Errorf("aggregate time/memory = %d/%d, want 22/40", result.CPUNs, result.MemoryBytes)
	}
	if len(fake.calls) != 3 {
		t.Fatalf("Run calls = %d, want compile + 2 cases", len(fake.calls))
	}

	compile := fake.calls[0]
	if strings.Join(compile.Command, " ") != "g++ Main.cpp -o Main -O2 -std=c++17" {
		t.Errorf("compile command = %v", compile.Command)
	}
	if compile.Inputs["Main.cpp"].Ref != "ref-1" || !reflect.DeepEqual(compile.Artifacts, []string{"Main"}) {
		t.Errorf("compile spec = %+v", compile)
	}
	if compile.Limits.CPUNs != cfg.Compile.CPUNs ||
		compile.Limits.ClockNs != cfg.Compile.ClockNs ||
		compile.Limits.MemoryBytes != cfg.Compile.MemoryBytes {
		t.Errorf("compile limits = %+v", compile.Limits)
	}

	for i, wantInput := range []string{"1 2\n", "3 4\n"} {
		run := fake.calls[i+1]
		if run.Inputs["Main"].Ref != "executable-ref" {
			t.Errorf("case %d executable input = %+v", i+1, run.Inputs)
		}
		if run.Stdin == nil || run.Stdin.Text != wantInput || run.Stdin.Ref != "" {
			t.Errorf("case %d stdin = %+v", i+1, run.Stdin)
		}
		if run.Limits.CPUNs != req.Limits.CPUNs ||
			run.Limits.MemoryBytes != req.Limits.MemoryBytes ||
			run.Limits.ClockNs != req.Limits.CPUNs*cfg.ClockRatio {
			t.Errorf("case %d limits = %+v", i+1, run.Limits)
		}
		if run.Limits.StdoutMaxBytes != cfg.Output.StdoutMaxBytes ||
			run.Limits.StderrMaxBytes != cfg.Output.StderrMaxBytes {
			t.Errorf("case %d output limits = %+v", i+1, run.Limits)
		}
	}

	if got := deletedRefs(fake.deleted); !reflect.DeepEqual(got, []string{"executable-ref", "ref-1"}) {
		t.Errorf("deleted refs = %v", got)
	}
	for _, c := range result.CaseResults {
		if c.Output != nil {
			t.Errorf("AC case should not include output: %+v", c)
		}
	}
}

func TestJudgeInterpretedLanguageSkipsCompile(t *testing.T) {
	fake := &fakeSandbox{runs: []runReply{runOK("answer\n")}}
	result := flow.Judge(context.Background(), fake, judgeConfig(), oneCaseRequest("python"), nil)

	if result.Verdict != contract.VerdictAC {
		t.Fatalf("result = %+v", result)
	}
	if len(fake.calls) != 1 {
		t.Fatalf("python should run once without compiling, calls = %d", len(fake.calls))
	}
	if fake.calls[0].Inputs["Main.py"].Ref != "ref-1" {
		t.Errorf("python source input = %+v", fake.calls[0].Inputs)
	}
}

func TestJudgeDefaultsEmptyModeToSubmit(t *testing.T) {
	data := writeTestData(t, map[string][2]string{
		"1": {"input", "answer"},
	})
	cfg := submitConfig(t)
	req := contract.JudgeRequest{
		SubmissionID:     "submission-1",
		ProblemID:        "problem-1",
		TestDataLocation: data.location,
		LanguageID:       "python",
		Source:           "print('answer')",
		Limits:           contract.JudgeLimits{CPUNs: 1_000, MemoryBytes: 2_000},
		// Mode 故意留空：flow 入口应当兜底成 submit。
	}
	fake := &fakeSandbox{runs: []runReply{runOK("answer")}}

	result := flow.Judge(context.Background(), fake, cfg, req, nil)
	if result.Verdict != contract.VerdictAC || len(result.CaseResults) != 1 {
		t.Fatalf("result = %+v", result)
	}
}

func TestJudgeExplicitClockLimitOverridesConfiguredRatio(t *testing.T) {
	cfg := judgeConfig()
	cfg.ClockRatio = 999
	req := oneCaseRequest("python")
	req.Limits.ClockNs = 77
	fake := &fakeSandbox{runs: []runReply{runOK("answer\n")}}

	result := flow.Judge(context.Background(), fake, cfg, req, nil)
	if result.Verdict != contract.VerdictAC {
		t.Fatalf("result = %+v", result)
	}
	if got := fake.calls[0].Limits.ClockNs; got != 77 {
		t.Errorf("ClockNs = %d, want explicit request value 77", got)
	}
}

func TestJudgeMapsRunStatuses(t *testing.T) {
	tests := []struct {
		name    string
		status  contract.Status
		want    contract.Verdict
		message string
	}{
		{"TLE", contract.StatusTimeLimitExceeded, contract.VerdictTLE, ""},
		{"MLE", contract.StatusMemoryLimitExceeded, contract.VerdictMLE, ""},
		{"OLE", contract.StatusOutputLimitExceeded, contract.VerdictOLE, ""},
		{"nonzero", contract.StatusNonzeroExit, contract.VerdictRE, "runtime failed"},
		{"signal", contract.StatusSignalled, contract.VerdictRE, "runtime failed"},
		{"workspace", contract.StatusWorkspaceError, contract.VerdictSE, "workspace failed"},
		{"internal", contract.StatusInternalError, contract.VerdictSE, "internal failed"},
		{"unknown", contract.Status("FutureStatus"), contract.VerdictSE, ""},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fake := &fakeSandbox{runs: []runReply{{result: contract.RunResult{
				Status: tt.status,
				Stdout: "partial output",
				Stderr: "runtime failed",
				Error:  tt.message,
			}}}}
			result := flow.Judge(context.Background(), fake, judgeConfig(), oneCaseRequest("python"), nil)
			if result.Verdict != tt.want || len(result.CaseResults) != 1 || result.CaseResults[0].Verdict != tt.want {
				t.Fatalf("result = %+v, want %s", result, tt.want)
			}
			if result.CaseResults[0].Output == nil {
				t.Error("non-AC case should include captured output")
			}
			if tt.message != "" && !strings.Contains(result.CaseResults[0].Message, tt.message) {
				t.Errorf("message = %q, want %q", result.CaseResults[0].Message, tt.message)
			}
		})
	}
}

func TestJudgeCompileOutcomes(t *testing.T) {
	tests := []struct {
		name        string
		reply       runReply
		want        contract.Verdict
		wantMessage string
	}{
		{"HTTP failure is SE", runReply{err: errors.New("sandbox down")}, contract.VerdictSE, "sandbox down"},
		{"nonzero is CE", runReply{result: contract.RunResult{Status: contract.StatusNonzeroExit, Stderr: "syntax error details"}}, contract.VerdictCE, "synt"},
		{"compile TLE is CE", runReply{result: contract.RunResult{Status: contract.StatusTimeLimitExceeded, Stderr: "too complex"}}, contract.VerdictCE, "too "},
		{"infrastructure status is SE", runReply{result: contract.RunResult{Status: contract.StatusInternalError, Error: "store failed"}}, contract.VerdictSE, "store failed"},
		{"missing artifact is SE", runReply{result: contract.RunResult{Status: contract.StatusOK}}, contract.VerdictSE, "without artifact"},
		{"unknown status is SE", runReply{result: contract.RunResult{Status: contract.Status("FutureStatus")}}, contract.VerdictSE, "unknown sandbox status"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cfg := judgeConfig()
			cfg.MessageExcerptBytes = 4
			fake := &fakeSandbox{runs: []runReply{tt.reply}}
			result := flow.Judge(context.Background(), fake, cfg, oneCaseRequest("cpp"), nil)
			if result.Verdict != tt.want {
				t.Fatalf("result = %+v, want %s", result, tt.want)
			}
			if !strings.Contains(result.Message, tt.wantMessage) {
				t.Errorf("message = %q, want substring %q", result.Message, tt.wantMessage)
			}
			if len(fake.calls) != 1 {
				t.Errorf("compile failure should stop before cases, calls = %d", len(fake.calls))
			}
		})
	}
}

func TestJudgeValidatesBeforeCallingSandbox(t *testing.T) {
	tests := []struct {
		name string
		cfg  config.Settings
		req  contract.JudgeRequest
	}{
		{"invalid mode", judgeConfig(), func() contract.JudgeRequest {
			r := oneCaseRequest("python")
			r.Mode = "official"
			return r
		}()},
		{"zero limits", judgeConfig(), func() contract.JudgeRequest {
			r := oneCaseRequest("python")
			r.Limits = contract.JudgeLimits{}
			return r
		}()},
		{"unknown language", judgeConfig(), oneCaseRequest("brainfuck")},
		{"no trial cases", judgeConfig(), trialRequest("python")},
		{"invalid clock ratio", func() config.Settings {
			c := judgeConfig()
			c.ClockRatio = 0
			return c
		}(), oneCaseRequest("python")},
		{"clock overflow", judgeConfig(), func() contract.JudgeRequest {
			r := oneCaseRequest("python")
			r.Limits.CPUNs = math.MaxInt64
			return r
		}()},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fake := &fakeSandbox{}
			result := flow.Judge(context.Background(), fake, tt.cfg, tt.req, nil)
			if result.Verdict != contract.VerdictSE || result.Message == "" {
				t.Fatalf("result = %+v", result)
			}
			if len(fake.uploaded) != 0 || len(fake.calls) != 0 {
				t.Errorf("invalid request reached sandbox: uploads=%d runs=%d", len(fake.uploaded), len(fake.calls))
			}
		})
	}
}

func TestJudgeUploadFailureIsSE(t *testing.T) {
	fake := &fakeSandbox{uploadErrors: map[int]error{1: errors.New("store unavailable")}}
	result := flow.Judge(context.Background(), fake, judgeConfig(), oneCaseRequest("python"), nil)
	if result.Verdict != contract.VerdictSE || !strings.Contains(result.Message, "store unavailable") {
		t.Fatalf("result = %+v", result)
	}
	if len(fake.calls) != 0 || len(fake.deleted) != 0 {
		t.Errorf("failed upload should not run or delete an unknown ref")
	}
}

func TestJudgeSmallInputInlinesAndLargeInputUsesRef(t *testing.T) {
	cfg := judgeConfig()
	cfg.InlineThresholdBytes = 3
	req := trialRequest("python",
		contract.CaseSpec{Input: "abc", Expected: "ok"},
		contract.CaseSpec{Input: "abcd", Expected: "ok"},
	)
	fake := &fakeSandbox{runs: []runReply{runOK("ok"), runOK("ok")}}

	result := flow.Judge(context.Background(), fake, cfg, req, nil)
	if result.Verdict != contract.VerdictAC {
		t.Fatalf("result = %+v", result)
	}
	if fake.calls[0].Stdin == nil || fake.calls[0].Stdin.Text != "abc" || fake.calls[0].Stdin.Ref != "" {
		t.Errorf("small stdin = %+v", fake.calls[0].Stdin)
	}
	if fake.calls[1].Stdin == nil || fake.calls[1].Stdin.Ref != "ref-2" || fake.calls[1].Stdin.Text != "" {
		t.Errorf("large stdin = %+v", fake.calls[1].Stdin)
	}
	if len(fake.uploaded) != 2 || string(fake.uploaded[1]) != "abcd" {
		t.Errorf("uploads = %q", fake.uploaded)
	}
	if got := deletedRefs(fake.deleted); !reflect.DeepEqual(got, []string{"ref-2", "ref-1"}) {
		t.Errorf("deleted refs = %v", got)
	}
}

func TestJudgeCleanupSurvivesRequestCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cfg := judgeConfig()
	cfg.InlineThresholdBytes = 1
	fake := &fakeSandbox{
		runs: []runReply{{err: context.Canceled}},
		onRun: func(call int) {
			cancel()
		},
	}

	result := flow.Judge(ctx, fake, cfg, trialRequest("python", contract.CaseSpec{Input: "large"}), nil)
	if result.Verdict != contract.VerdictSE {
		t.Fatalf("result = %+v", result)
	}
	if got := deletedRefs(fake.deleted); !reflect.DeepEqual(got, []string{"ref-2", "ref-1"}) {
		t.Fatalf("deleted refs = %v", got)
	}
	for _, d := range fake.deleted {
		if d.ctxErr != nil {
			t.Errorf("cleanup ref %s used canceled context: %v", d.ref, d.ctxErr)
		}
	}
}

func TestJudgeConcealsAndRevealsExpectedOutput(t *testing.T) {
	data := writeTestData(t, map[string][2]string{
		"1": {"input\n", "secret-answer\n"},
	})
	req := contract.JudgeRequest{
		SubmissionID:     "submission-1",
		ProblemID:        "problem-1",
		TestDataLocation: data.location,
		LanguageID:       "python",
		Source:           "print('wrong')",
		Limits:           contract.JudgeLimits{CPUNs: 1_000, MemoryBytes: 2_000},
		Mode:             contract.ModeSubmit,
	}

	for _, reveal := range []bool{false, true} {
		t.Run(fmt.Sprintf("reveal=%v", reveal), func(t *testing.T) {
			cfg := submitConfig(t)
			cfg.RevealExpected = reveal
			fake := &fakeSandbox{runs: []runReply{runOK("wrong-answer\n")}}

			result := flow.Judge(context.Background(), fake, cfg, req, nil)
			if result.Verdict != contract.VerdictWA || len(result.CaseResults) != 1 || result.CaseResults[0].Diff == nil {
				t.Fatalf("result = %+v", result)
			}
			want := ""
			if reveal {
				want = "secret-answer"
			}
			if result.CaseResults[0].Diff.Want != want {
				t.Errorf("Diff.Want = %q, want %q", result.CaseResults[0].Diff.Want, want)
			}
			encoded, err := json.Marshal(result)
			if err != nil {
				t.Fatal(err)
			}
			if !reveal && (strings.Contains(string(encoded), `"want"`) || strings.Contains(string(encoded), "secret-answer")) {
				t.Errorf("concealed response leaked expected output: %s", encoded)
			}
			calls, err := json.Marshal(fake.calls)
			if err != nil {
				t.Fatal(err)
			}
			if strings.Contains(string(calls), "secret-answer") {
				t.Errorf("expected output was sent to sandbox: %s", calls)
			}
		})
	}
}

func TestJudgeWhitespacePolicyAndRAN(t *testing.T) {
	t.Run("strict whitespace produces PE", func(t *testing.T) {
		cfg := judgeConfig()
		cfg.StrictWhitespace = true
		fake := &fakeSandbox{runs: []runReply{runOK("3")}}
		result := flow.Judge(context.Background(), fake, cfg,
			trialRequest("python", contract.CaseSpec{Input: "", Expected: "3\n"}), nil)
		if result.Verdict != contract.VerdictPE || result.CaseResults[0].Diff != nil {
			t.Fatalf("result = %+v", result)
		}
	})

	t.Run("missing expected produces RAN", func(t *testing.T) {
		fake := &fakeSandbox{runs: []runReply{runOK("diagnostic output")}}
		result := flow.Judge(context.Background(), fake, judgeConfig(),
			trialRequest("python", contract.CaseSpec{Input: "input"}), nil)
		if result.Verdict != contract.VerdictRAN || result.Score != 0 || result.CaseResults[0].Output == nil {
			t.Fatalf("result = %+v", result)
		}
	})
}

func TestJudgeRunsAllCasesAndKeepsWorstVerdict(t *testing.T) {
	req := trialRequest("python",
		contract.CaseSpec{Input: "1", Expected: "x"},
		contract.CaseSpec{Input: "2", Expected: "x"},
	)
	fake := &fakeSandbox{runs: []runReply{
		{result: contract.RunResult{Status: contract.StatusTimeLimitExceeded, CPUNs: 50, MemoryBytes: 10}},
		{result: contract.RunResult{Status: contract.StatusMemoryLimitExceeded, CPUNs: 20, MemoryBytes: 90}},
	}}
	result := flow.Judge(context.Background(), fake, judgeConfig(), req, nil)
	if result.Verdict != contract.VerdictMLE || len(result.CaseResults) != 2 || len(fake.calls) != 2 {
		t.Fatalf("result = %+v calls=%d", result, len(fake.calls))
	}
	if result.CPUNs != 50 || result.MemoryBytes != 90 {
		t.Errorf("aggregate = %d/%d", result.CPUNs, result.MemoryBytes)
	}
}

func TestJudgeOutputExcerptPreservesUTF8Boundary(t *testing.T) {
	cfg := judgeConfig()
	cfg.OutputExcerptBytes = 3 // "a你" 需要 4 字节，不能留下半个“你”
	fake := &fakeSandbox{runs: []runReply{runOK("a你b")}}
	result := flow.Judge(context.Background(), fake, cfg,
		trialRequest("python", contract.CaseSpec{Input: "", Expected: "different"}), nil)

	output := result.CaseResults[0].Output
	if output == nil {
		t.Fatal("WA should include output")
	}
	if output.Excerpt != "a" || output.Bytes != int64(len("a你b")) || !output.Truncated {
		t.Errorf("output = %+v", output)
	}
	if !utf8.ValidString(output.Excerpt) {
		t.Errorf("excerpt is invalid UTF-8: %q", output.Excerpt)
	}
}

// 判题读的是私有副本；副本里的答案文件在运行中消失，这个点必须判 SE，而不是当作答案为空。
func TestJudgeExpectedFileDisappearsIsSE(t *testing.T) {
	data := writeTestData(t, map[string][2]string{
		"1": {"input", "answer"},
	})
	cfg := submitConfig(t)
	req := contract.JudgeRequest{
		SubmissionID:     "submission-1",
		ProblemID:        "problem-1",
		TestDataLocation: data.location,
		LanguageID:       "python",
		Source:           "print('answer')",
		Limits:           contract.JudgeLimits{CPUNs: 1_000, MemoryBytes: 2_000},
		Mode:             contract.ModeSubmit,
	}
	fake := &fakeSandbox{
		runs: []runReply{runOK("answer")},
		onRun: func(call int) {
			copies, err := filepath.Glob(filepath.Join(cfg.Testdata.WorkRoot, "run-*", "1.out"))
			if err != nil || len(copies) != 1 {
				t.Fatalf("local copy of 1.out: %v %v", copies, err)
			}
			if err := os.Remove(copies[0]); err != nil {
				t.Errorf("remove expected output: %v", err)
			}
		},
	}

	result := flow.Judge(context.Background(), fake, cfg, req, nil)
	if result.Verdict != contract.VerdictSE || !strings.Contains(result.CaseResults[0].Message, "open expected output") {
		t.Fatalf("result = %+v", result)
	}
}

func TestJudgeReportsTheDigestOfTheTestData(t *testing.T) {
	data := writeTestData(t, map[string][2]string{"1": {"input", "answer"}})
	req := contract.JudgeRequest{
		SubmissionID: "submission-1", ProblemID: "problem-1", TestDataLocation: data.location,
		LanguageID: "python", Source: "print('answer')",
		Limits: contract.JudgeLimits{CPUNs: 1_000, MemoryBytes: 2_000},
	}
	fake := &fakeSandbox{runs: []runReply{runOK("answer")}}

	result := flow.Judge(context.Background(), fake, submitConfig(t), req, nil)
	if result.Verdict != contract.VerdictAC || result.TestDataDigest != data.digest {
		t.Fatalf("verdict=%s digest=%q, want AC and %q", result.Verdict, result.TestDataDigest, data.digest)
	}
}

// 编译失败时测试数据已经读过，指纹照样带回；trial 模式没有数据，不带。
func TestJudgeDigestIsReportedOnCompileErrorButNotForTrial(t *testing.T) {
	data := writeTestData(t, map[string][2]string{"1": {"input", "answer"}})
	submit := contract.JudgeRequest{
		SubmissionID: "submission-1", ProblemID: "problem-1", TestDataLocation: data.location,
		LanguageID: "cpp", Source: "int main(",
		Limits: contract.JudgeLimits{CPUNs: 1_000, MemoryBytes: 2_000},
	}
	failedCompile := runReply{result: contract.RunResult{Status: contract.StatusNonzeroExit, ExitCode: 1, Stderr: "error"}}

	result := flow.Judge(context.Background(), &fakeSandbox{runs: []runReply{failedCompile}}, submitConfig(t), submit, nil)
	if result.Verdict != contract.VerdictCE || result.TestDataDigest != data.digest {
		t.Errorf("CE: verdict=%s digest=%q, want CE and %q", result.Verdict, result.TestDataDigest, data.digest)
	}

	trial := flow.Judge(context.Background(), &fakeSandbox{runs: []runReply{runOK("answer")}}, judgeConfig(), oneCaseRequest("python"), nil)
	if trial.TestDataDigest != "" {
		t.Errorf("trial 结果不该带测试数据指纹: %q", trial.TestDataDigest)
	}
}

// 本地副本属于这次判题：无论结论是什么，判完都要删，否则每次判题在磁盘上留一份数据。
func TestJudgeRemovesTheLocalCopyWhateverTheVerdict(t *testing.T) {
	tests := []struct {
		name     string
		language string
		runs     []runReply
		want     contract.Verdict
	}{
		{"AC", "python", []runReply{runOK("answer")}, contract.VerdictAC},
		{"WA", "python", []runReply{runOK("wrong")}, contract.VerdictWA},
		{"CE", "cpp", []runReply{{result: contract.RunResult{Status: contract.StatusNonzeroExit, ExitCode: 1}}}, contract.VerdictCE},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			data := writeTestData(t, map[string][2]string{"1": {"input", "answer"}})
			cfg := submitConfig(t)
			req := contract.JudgeRequest{
				SubmissionID: "submission-1", ProblemID: "problem-1", TestDataLocation: data.location,
				LanguageID: tt.language, Source: "source",
				Limits: contract.JudgeLimits{CPUNs: 1_000, MemoryBytes: 2_000},
			}

			result := flow.Judge(context.Background(), &fakeSandbox{runs: tt.runs}, cfg, req, nil)
			if result.Verdict != tt.want {
				t.Fatalf("verdict = %s, want %s", result.Verdict, tt.want)
			}
			if left, _ := os.ReadDir(cfg.Testdata.WorkRoot); len(left) != 0 {
				t.Errorf("判完后工作目录里还剩 %d 项", len(left))
			}
		})
	}
}

// 测试数据读不到：整次判题是 SE，且连源码都不该上传给执行层。
func TestJudgeWithUnreadableTestDataIsSEWithoutTouchingTheSandbox(t *testing.T) {
	for name, location := range map[string]string{
		"数据未就绪": filepath.Join(t.TempDir(), "not-written"),
		"相对路径":  "problem/data",
	} {
		t.Run(name, func(t *testing.T) {
			req := contract.JudgeRequest{
				SubmissionID: "submission-1", ProblemID: "problem-1", TestDataLocation: location,
				LanguageID: "python", Source: "print('answer')",
				Limits: contract.JudgeLimits{CPUNs: 1_000, MemoryBytes: 2_000},
			}
			fake := &fakeSandbox{}

			result := flow.Judge(context.Background(), fake, submitConfig(t), req, nil)
			if result.Verdict != contract.VerdictSE || !strings.Contains(result.Message, "load testcases") {
				t.Fatalf("result = %+v", result)
			}
			if len(fake.uploaded) != 0 || len(fake.calls) != 0 {
				t.Errorf("测试数据不可用时不该碰执行层: uploads=%d runs=%d", len(fake.uploaded), len(fake.calls))
			}
		})
	}
}

// dataset 是一份按测试数据协议写好的目录。
type dataset struct{ location, digest string }

// writeTestData 在临时目录下按协议写出数据文件和 testdata.json；cases 的键是测试点名，按名字排序成 cases 的顺序。
func writeTestData(t *testing.T, cases map[string][2]string) dataset {
	t.Helper()
	dir := filepath.Join(t.TempDir(), "problem")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	names := make([]string, 0, len(cases))
	for name := range cases {
		names = append(names, name)
	}
	slices.Sort(names)

	sum := func(content string) string {
		h := sha256.Sum256([]byte(content))
		return hex.EncodeToString(h[:])
	}
	var entries []map[string]any
	var lines strings.Builder
	var total int
	for _, name := range names {
		in, out := cases[name][0], cases[name][1]
		for file, content := range map[string]string{name + ".in": in, name + ".out": out} {
			if err := os.WriteFile(filepath.Join(dir, file), []byte(content), 0o600); err != nil {
				t.Fatal(err)
			}
		}
		entries = append(entries, map[string]any{
			"name":   name,
			"input":  map[string]any{"sizeBytes": len(in), "sha256": sum(in)},
			"output": map[string]any{"sizeBytes": len(out), "sha256": sum(out)},
		})
		fmt.Fprintf(&lines, "%s  %s.in\n%s  %s.out\n", sum(in), name, sum(out), name)
		total += len(in) + len(out)
	}
	digest := sum(lines.String())
	meta, err := json.Marshal(map[string]any{
		"schemaVersion": 1, "caseCount": len(entries), "totalBytes": total, "digest": digest, "cases": entries,
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "testdata.json"), meta, 0o600); err != nil {
		t.Fatal(err)
	}
	return dataset{location: dir, digest: digest}
}

// submitConfig 给 submit 模式的用例一个独立的工作目录，判题结束后可以检查里面有没有残留。
func submitConfig(t *testing.T) config.Settings {
	t.Helper()
	cfg := judgeConfig()
	cfg.Testdata.WorkRoot = filepath.Join(t.TempDir(), "work")
	return cfg
}

func deletedRefs(deleted []deletion) []string {
	refs := make([]string, len(deleted))
	for i, d := range deleted {
		refs[i] = d.ref
	}
	return refs
}

func TestTrialPreservesSuccessfulStderr(t *testing.T) {
	sb := &fakeSandbox{runs: []runReply{{result: contract.RunResult{Status: contract.StatusOK, Stderr: "debug\n", Stdout: "answer\n"}}}}
	result := flow.Judge(context.Background(), sb, judgeConfig(), trialRequest("python", contract.CaseSpec{Input: ""}), nil)
	if result.Verdict != contract.VerdictRAN || len(result.CaseResults) != 1 {
		t.Fatalf("unexpected result: %s", result.Verdict)
	}
	c := result.CaseResults[0]
	if c.Stderr == nil || c.Stderr.Excerpt != "debug\n" || c.Output == nil || c.Output.Excerpt != "answer\n" {
		t.Fatal("trial lost independent output streams")
	}
}

func TestEmptyTrialInputSurvivesSandboxWireEncoding(t *testing.T) {
	sb := &fakeSandbox{runs: []runReply{{result: contract.RunResult{Status: contract.StatusOK}}}}
	result := flow.Judge(context.Background(), sb, judgeConfig(), trialRequest("python", contract.CaseSpec{Input: ""}), nil)
	if result.Verdict != contract.VerdictRAN {
		t.Fatal(result.Verdict)
	}
	wire, err := json.Marshal(sb.calls[0])
	if err != nil {
		t.Fatal(err)
	}
	var decoded contract.RunSpec
	if err := json.Unmarshal(wire, &decoded); err != nil {
		t.Fatalf("sandbox rejects empty stdin: %v", err)
	}
	if decoded.Stdin != nil {
		t.Fatal("empty input must use EOF, not an empty file source object")
	}
}

// 删除 blob 失败不改变结论（store 过了保留期会清理），但必须留痕：
// 持续失败说明 sandbox 的 store 或连接出了问题。
func TestJudgeLogsFailedBlobDeleteWithoutChangingVerdict(t *testing.T) {
	var logs bytes.Buffer
	fake := &fakeSandbox{runs: []runReply{runOK("answer\n")}, deleteErr: errors.New("store unavailable")}
	req := oneCaseRequest("python")
	req.SubmissionID = "s-42"
	result := flow.Judge(context.Background(), fake, judgeConfig(), req, slog.New(slog.NewJSONHandler(&logs, nil)))
	if result.Verdict != contract.VerdictAC {
		t.Fatalf("删除失败改变了结论: %+v", result)
	}
	out := logs.String()
	if !strings.Contains(out, `"msg":"judge.blob.delete.failed"`) || !strings.Contains(out, "s-42") || !strings.Contains(out, "store unavailable") {
		t.Fatalf("删除失败没有留痕: %s", out)
	}
}
