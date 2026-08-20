package flow_test

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"unicode/utf8"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/judge/flow"
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
	return nil
}

func judgeConfig() config.JudgeConfig {
	return config.Default().Judge
}

func trialRequest(language string, cases ...contract.CaseSpec) contract.JudgeRequest {
	return contract.JudgeRequest{
		SubmissionID:      "submission-1",
		ProblemID:         "problem-1",
		ProblemVersionID:  "problem-version-1",
		TestDataVersionID: "test-data-version-1",
		LanguageID:        language,
		Source:            "source code",
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

	result := flow.Judge(context.Background(), fake, cfg, req)

	if result.Verdict != contract.VerdictAC || result.Score != 100 {
		t.Fatalf("result = %+v", result)
	}
	if result.EnvironmentFingerprint != cfg.EnvironmentFingerprint {
		t.Errorf("environmentFingerprint=%q want %q", result.EnvironmentFingerprint, cfg.EnvironmentFingerprint)
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
	result := flow.Judge(context.Background(), fake, judgeConfig(), oneCaseRequest("python"))

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
	root := writeTestData(t, "default-mode", map[string][2]string{
		"1": {"input", "answer"},
	})
	cfg := judgeConfig()
	cfg.TestdataRoot = root
	req := contract.JudgeRequest{
		SubmissionID:      "submission-1",
		ProblemID:         "problem-1",
		ProblemVersionID:  "problem-version-1",
		TestDataVersionID: "default-mode",
		LanguageID:        "python",
		Source:            "print('answer')",
		Limits:            contract.JudgeLimits{CPUNs: 1_000, MemoryBytes: 2_000},
		// Mode 故意留空：flow 入口应当兜底成 submit。
	}
	fake := &fakeSandbox{runs: []runReply{runOK("answer")}}

	result := flow.Judge(context.Background(), fake, cfg, req)
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

	result := flow.Judge(context.Background(), fake, cfg, req)
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
			result := flow.Judge(context.Background(), fake, judgeConfig(), oneCaseRequest("python"))
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
			result := flow.Judge(context.Background(), fake, cfg, oneCaseRequest("cpp"))
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
		cfg  config.JudgeConfig
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
		{"invalid clock ratio", func() config.JudgeConfig {
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
			result := flow.Judge(context.Background(), fake, tt.cfg, tt.req)
			if result.Verdict != contract.VerdictSE || result.Message == "" {
				t.Fatalf("result = %+v", result)
			}
			if result.EnvironmentFingerprint != tt.cfg.EnvironmentFingerprint {
				t.Errorf("提前返回也必须带实际环境指纹，got %q", result.EnvironmentFingerprint)
			}
			if len(fake.uploaded) != 0 || len(fake.calls) != 0 {
				t.Errorf("invalid request reached sandbox: uploads=%d runs=%d", len(fake.uploaded), len(fake.calls))
			}
		})
	}
}

func TestJudgeUploadFailureIsSE(t *testing.T) {
	fake := &fakeSandbox{uploadErrors: map[int]error{1: errors.New("store unavailable")}}
	result := flow.Judge(context.Background(), fake, judgeConfig(), oneCaseRequest("python"))
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

	result := flow.Judge(context.Background(), fake, cfg, req)
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

	result := flow.Judge(ctx, fake, cfg, trialRequest("python", contract.CaseSpec{Input: "large"}))
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
	root := writeTestData(t, "secret", map[string][2]string{
		"1": {"input\n", "secret-answer\n"},
	})
	req := contract.JudgeRequest{
		SubmissionID:      "submission-1",
		ProblemID:         "problem-1",
		ProblemVersionID:  "problem-version-1",
		TestDataVersionID: "secret",
		LanguageID:        "python",
		Source:            "print('wrong')",
		Limits:            contract.JudgeLimits{CPUNs: 1_000, MemoryBytes: 2_000},
		Mode:              contract.ModeSubmit,
	}

	for _, reveal := range []bool{false, true} {
		t.Run(fmt.Sprintf("reveal=%v", reveal), func(t *testing.T) {
			cfg := judgeConfig()
			cfg.TestdataRoot = root
			cfg.RevealExpected = reveal
			fake := &fakeSandbox{runs: []runReply{runOK("wrong-answer\n")}}

			result := flow.Judge(context.Background(), fake, cfg, req)
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
			trialRequest("python", contract.CaseSpec{Input: "", Expected: "3\n"}))
		if result.Verdict != contract.VerdictPE || result.CaseResults[0].Diff != nil {
			t.Fatalf("result = %+v", result)
		}
	})

	t.Run("missing expected produces RAN", func(t *testing.T) {
		fake := &fakeSandbox{runs: []runReply{runOK("diagnostic output")}}
		result := flow.Judge(context.Background(), fake, judgeConfig(),
			trialRequest("python", contract.CaseSpec{Input: "input"}))
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
	result := flow.Judge(context.Background(), fake, judgeConfig(), req)
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
		trialRequest("python", contract.CaseSpec{Input: "", Expected: "different"}))

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

func TestJudgeExpectedFileDisappearsIsSE(t *testing.T) {
	root := writeTestData(t, "vanishing", map[string][2]string{
		"1": {"input", "answer"},
	})
	outPath := filepath.Join(root, "vanishing", "1.out")
	cfg := judgeConfig()
	cfg.TestdataRoot = root
	req := contract.JudgeRequest{
		SubmissionID:      "submission-1",
		ProblemID:         "problem-1",
		ProblemVersionID:  "problem-version-1",
		TestDataVersionID: "vanishing",
		LanguageID:        "python",
		Source:            "print('answer')",
		Limits:            contract.JudgeLimits{CPUNs: 1_000, MemoryBytes: 2_000},
		Mode:              contract.ModeSubmit,
	}
	fake := &fakeSandbox{
		runs: []runReply{runOK("answer")},
		onRun: func(call int) {
			if err := os.Remove(outPath); err != nil {
				t.Errorf("remove expected output: %v", err)
			}
		},
	}

	result := flow.Judge(context.Background(), fake, cfg, req)
	if result.Verdict != contract.VerdictSE || !strings.Contains(result.CaseResults[0].Message, "open expected output") {
		t.Fatalf("result = %+v", result)
	}
}

func writeTestData(t *testing.T, testDataVersionID string, cases map[string][2]string) string {
	t.Helper()
	root := t.TempDir()
	dir := filepath.Join(root, testDataVersionID)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	for name, pair := range cases {
		if err := os.WriteFile(filepath.Join(dir, name+".in"), []byte(pair[0]), 0o600); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(dir, name+".out"), []byte(pair[1]), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	return root
}

func deletedRefs(deleted []deletion) []string {
	refs := make([]string, len(deleted))
	for i, d := range deleted {
		refs[i] = d.ref
	}
	return refs
}
