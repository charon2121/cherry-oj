// Package flow 编排一次完整判题：准备测例、编译、逐点运行、比对并汇总结论。
// sandbox 只报告执行事实；从 RunStatus 翻译成 Verdict 的职责全部留在这里。
package flow

import (
	"context"
	"fmt"
	"io"
	"math"
	"strings"
	"unicode/utf8"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/judge/checker"
	"cherry-oj/judge-engine/internal/judge/language"
	"cherry-oj/judge-engine/internal/judge/testcase"
)

// Sandbox 是 flow 完成判题所需要的能力。
// 接口由消费方定义，因此测试可以用纯内存替身覆盖所有判题分支。
type Sandbox interface {
	Upload(ctx context.Context, body io.Reader) (ref string, err error)
	Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error)
	Delete(ctx context.Context, ref string) error
}

// Judge 执行一次判题。请求或基础设施问题也会被收敛成 VerdictSE，
// 让上层始终拿到可持久化的 JudgeResult。
func Judge(ctx context.Context, sb Sandbox, cfg config.JudgeConfig, req contract.JudgeRequest) (result contract.JudgeResult) {
	// 指纹描述实际运行本进程的环境，不能从请求回显。即使提前返回 CE/SE，
	// judging-service 也必须能验证任务没有被路由到错误环境。
	defer func() { result.EnvironmentFingerprint = cfg.EnvironmentFingerprint }()

	se := func(format string, args ...any) contract.JudgeResult {
		return contract.JudgeResult{
			Verdict: contract.VerdictSE,
			Message: fmt.Sprintf(format, args...),
		}
	}

	if req.Mode == "" {
		req.Mode = contract.ModeSubmit
	}
	if !req.Mode.IsValid() {
		return se("invalid judge mode: %q", req.Mode)
	}
	if err := req.Limits.Validate(); err != nil {
		return se("invalid judge limits: %v", err)
	}

	lang, ok := language.Get(req.LanguageID)
	if !ok {
		return se("unknown language: %q", req.LanguageID)
	}

	cases, err := loadCases(cfg, req)
	if err != nil {
		return se("load test cases: %v", err)
	}
	if len(cases) == 0 {
		return se("no test cases")
	}

	clock, err := effectiveClockNs(req.Limits, cfg.ClockRatio)
	if err != nil {
		return se("calculate clock limit: %v", err)
	}

	sourceRef, err := sb.Upload(ctx, strings.NewReader(req.Source))
	if err != nil {
		return se("upload source: %v", err)
	}
	if sourceRef == "" {
		return se("upload source: sandbox returned an empty ref")
	}
	defer deleteRef(ctx, sb, sourceRef)

	executableRef, early := compile(ctx, sb, cfg, lang, sourceRef)
	if early != nil {
		return *early
	}
	if executableRef != "" {
		defer deleteRef(ctx, sb, executableRef)
	}

	result = contract.JudgeResult{Verdict: contract.VerdictAC}
	for i, tc := range cases {
		caseResult := runCase(ctx, sb, cfg, req.Limits, clock, lang, sourceRef, executableRef, i+1, tc)
		result.CaseResults = append(result.CaseResults, caseResult)
		result.CPUNs = max(result.CPUNs, caseResult.CPUNs)
		result.MemoryBytes = max(result.MemoryBytes, caseResult.MemoryBytes)
		result.Verdict = worse(result.Verdict, caseResult.Verdict)
	}
	result.Score = scoreOf(result.Verdict)
	return result
}

func loadCases(cfg config.JudgeConfig, req contract.JudgeRequest) ([]testcase.TestCase, error) {
	if req.Mode.UsesVersionedTestdata() {
		return testcase.Load(cfg.TestdataRoot, req.TestDataVersionID, testcase.Options{})
	}
	return testcase.FromSpecs(req.Cases), nil
}

// compile 返回编译产物 ref。解释型语言不编译，ref 为空；若判题应当提前结束，
// early 带着已经区分好 CE / SE 的结果。
func compile(
	ctx context.Context,
	sb Sandbox,
	cfg config.JudgeConfig,
	lang language.Language,
	sourceRef string,
) (ref string, early *contract.JudgeResult) {
	if !lang.NeedsCompile() {
		return "", nil
	}

	run, err := sb.Run(ctx, contract.RunSpec{
		Command: lang.Compile,
		Inputs: map[string]contract.FileSource{
			lang.SourceName: {Ref: sourceRef},
		},
		Artifacts: []string{lang.CompiledArtifact},
		Limits:    compileLimits(cfg),
	})
	if err != nil {
		result := systemError("compile request: %v", err)
		return "", &result
	}

	switch run.Status {
	case contract.StatusOK:
		ref = run.Artifacts[lang.CompiledArtifact]
		if ref == "" {
			result := systemError("compile succeeded without artifact %q", lang.CompiledArtifact)
			return "", &result
		}
		return ref, nil

	case contract.StatusWorkspaceError, contract.StatusInternalError:
		message := run.Error
		if message == "" {
			message = fmt.Sprintf("sandbox returned %s while compiling", run.Status)
		}
		result := systemError("compile infrastructure failure: %s", message)
		return "", &result

	case contract.StatusTimeLimitExceeded,
		contract.StatusMemoryLimitExceeded,
		contract.StatusOutputLimitExceeded,
		contract.StatusNonzeroExit,
		contract.StatusSignalled:
		message := run.Stderr
		if message == "" {
			message = run.Error
		}
		result := contract.JudgeResult{
			Verdict: contract.VerdictCE,
			Message: firstN(message, cfg.MessageExcerptBytes),
		}
		return "", &result

	default:
		result := systemError("unknown sandbox status while compiling: %q", run.Status)
		return "", &result
	}
}

func runCase(
	ctx context.Context,
	sb Sandbox,
	cfg config.JudgeConfig,
	limits contract.JudgeLimits,
	clockNs int64,
	lang language.Language,
	sourceRef string,
	executableRef string,
	idx int,
	tc testcase.TestCase,
) contract.CaseResult {
	stdin, cleanup, err := stdinFor(ctx, sb, tc.Input, cfg.InlineThresholdBytes)
	if err != nil {
		return contract.CaseResult{
			Idx:     idx,
			Name:    tc.Name,
			Verdict: contract.VerdictSE,
			Message: fmt.Sprintf("prepare stdin: %v", err),
		}
	}

	run, runErr := sb.Run(ctx, contract.RunSpec{
		Command: lang.Run,
		Inputs:  runInputs(lang, sourceRef, executableRef),
		Stdin:   stdin,
		Limits: contract.Limits{
			CPUNs:          limits.CPUNs,
			ClockNs:        clockNs,
			MemoryBytes:    limits.MemoryBytes,
			StdoutMaxBytes: cfg.Output.StdoutMaxBytes,
			StderrMaxBytes: cfg.Output.StderrMaxBytes,
		},
	})
	cleanup() // 循环内资源必须在本轮释放，不能 defer 到整个 Judge 返回。

	if runErr != nil {
		return contract.CaseResult{
			Idx:     idx,
			Name:    tc.Name,
			Verdict: contract.VerdictSE,
			Message: fmt.Sprintf("run sandbox command: %v", runErr),
		}
	}
	return evalCase(idx, tc, run, cfg)
}

func runInputs(lang language.Language, sourceRef, executableRef string) map[string]contract.FileSource {
	if lang.NeedsCompile() {
		return map[string]contract.FileSource{
			lang.CompiledArtifact: {Ref: executableRef},
		}
	}
	return map[string]contract.FileSource{
		lang.SourceName: {Ref: sourceRef},
	}
}

func stdinFor(
	ctx context.Context,
	sb Sandbox,
	input testcase.Blob,
	inlineThreshold int64,
) (*contract.FileSource, func(), error) {
	rc, err := input.Open()
	if err != nil {
		return nil, func() {}, err
	}
	defer rc.Close()

	if input.Size <= inlineThreshold {
		body, err := io.ReadAll(rc)
		if err != nil {
			return nil, func() {}, err
		}
		return &contract.FileSource{Text: string(body)}, func() {}, nil
	}

	ref, err := sb.Upload(ctx, rc)
	if err != nil {
		return nil, func() {}, err
	}
	if ref == "" {
		return nil, func() {}, fmt.Errorf("sandbox returned an empty stdin ref")
	}
	cleanup := func() { deleteRef(ctx, sb, ref) }
	return &contract.FileSource{Ref: ref}, cleanup, nil
}

func evalCase(idx int, tc testcase.TestCase, run contract.RunResult, cfg config.JudgeConfig) contract.CaseResult {
	result := contract.CaseResult{
		Idx:         idx,
		Name:        tc.Name,
		CPUNs:       run.CPUNs,
		MemoryBytes: run.MemoryBytes,
	}

	switch run.Status {
	case contract.StatusOK:
		if tc.Expected == nil {
			result.Verdict = contract.VerdictRAN
			break
		}

		expected, err := tc.Expected.Open()
		if err != nil {
			result.Verdict = contract.VerdictSE
			result.Message = fmt.Sprintf("open expected output: %v", err)
			break
		}
		verdict, diff, compareErr := checker.Compare(
			checker.Options{StrictWhitespace: cfg.StrictWhitespace},
			strings.NewReader(run.Stdout),
			expected,
		)
		_ = expected.Close()
		if compareErr != nil {
			result.Verdict = contract.VerdictSE
			result.Message = fmt.Sprintf("compare output: %v", compareErr)
			break
		}

		result.Verdict = verdict
		if verdict == contract.VerdictWA {
			if !cfg.RevealExpected {
				diff.Want = ""
			}
			result.Diff = &diff
		}

	case contract.StatusTimeLimitExceeded:
		result.Verdict = contract.VerdictTLE
	case contract.StatusMemoryLimitExceeded:
		result.Verdict = contract.VerdictMLE
	case contract.StatusOutputLimitExceeded:
		result.Verdict = contract.VerdictOLE
	case contract.StatusNonzeroExit, contract.StatusSignalled:
		result.Verdict = contract.VerdictRE
		result.Message = firstN(run.Stderr, cfg.MessageExcerptBytes)
	case contract.StatusWorkspaceError, contract.StatusInternalError:
		result.Verdict = contract.VerdictSE
		result.Message = run.Error
	default:
		result.Verdict = contract.VerdictSE
		result.Message = fmt.Sprintf("unknown sandbox status: %q", run.Status)
	}

	if result.Verdict != contract.VerdictAC {
		result.Output = makeOutput(run.Stdout, cfg.OutputExcerptBytes)
	}
	return result
}

func compileLimits(cfg config.JudgeConfig) contract.Limits {
	return contract.Limits{
		CPUNs:          cfg.Compile.CPUNs,
		ClockNs:        cfg.Compile.ClockNs,
		MemoryBytes:    cfg.Compile.MemoryBytes,
		StdoutMaxBytes: cfg.Output.StdoutMaxBytes,
		StderrMaxBytes: cfg.Output.StderrMaxBytes,
	}
}

func effectiveClockNs(limits contract.JudgeLimits, ratio int64) (int64, error) {
	if limits.ClockNs > 0 {
		return limits.ClockNs, nil
	}
	if ratio <= 0 {
		return 0, fmt.Errorf("clock ratio must be positive, got %d", ratio)
	}
	if limits.CPUNs > math.MaxInt64/ratio {
		return 0, fmt.Errorf("cpuNs %d multiplied by clock ratio %d overflows int64", limits.CPUNs, ratio)
	}
	return limits.CPUNs * ratio, nil
}

func makeOutput(stdout string, excerptBytes int) *contract.Output {
	excerpt := firstN(stdout, excerptBytes)
	return &contract.Output{
		Excerpt:   excerpt,
		Bytes:     int64(len(stdout)),
		Truncated: len(excerpt) < len(stdout),
	}
}

// firstN 最多保留 n 个字节，并退回到 UTF-8 rune 边界，避免把多字节字符劈开。
func firstN(s string, n int) string {
	if n <= 0 {
		return ""
	}
	if len(s) <= n {
		return s
	}
	end := n
	for end > 0 && !utf8.RuneStart(s[end]) {
		end--
	}
	return s[:end]
}

var severity = map[contract.Verdict]int{
	contract.VerdictSE:  0,
	contract.VerdictCE:  1,
	contract.VerdictRE:  2,
	contract.VerdictMLE: 3,
	contract.VerdictOLE: 4,
	contract.VerdictTLE: 5,
	contract.VerdictWA:  6,
	contract.VerdictPE:  7,
	contract.VerdictRAN: 8,
	contract.VerdictAC:  9,
}

func worse(a, b contract.Verdict) contract.Verdict {
	aRank, aKnown := severity[a]
	bRank, bKnown := severity[b]
	if !aKnown || !bKnown {
		return contract.VerdictSE
	}
	if aRank <= bRank {
		return a
	}
	return b
}

func scoreOf(verdict contract.Verdict) int {
	if verdict == contract.VerdictAC {
		return 100
	}
	return 0
}

func systemError(format string, args ...any) contract.JudgeResult {
	return contract.JudgeResult{
		Verdict: contract.VerdictSE,
		Message: fmt.Sprintf(format, args...),
	}
}

func deleteRef(ctx context.Context, sb Sandbox, ref string) {
	_ = sb.Delete(context.WithoutCancel(ctx), ref)
}
