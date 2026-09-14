package flow

import (
	"context"
	"fmt"
	"io"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/judge/language"
	"cherry-oj/judge-engine/internal/judge/testcase"
)

func (j *judgment) runCase(ctx context.Context, idx int, tc testcase.TestCase) contract.CaseResult {
	sb, cfg, lang := j.sandbox, j.config, j.language
	limits, clockNs := j.request.Limits, j.clockNs
	sourceRef, executableRef := j.sourceRef, j.executableRef
	trial := j.request.Mode == contract.ModeTrial
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
	result := evalCase(idx, tc, run, cfg)
	if trial {
		result.Stderr = makeOutput(run.Stderr, cfg.OutputExcerptBytes)
	}
	return result
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
		// 空文本经 omitempty 会变成非法的 {}；省略 stdin 让执行器直接读到 EOF。
		if len(body) == 0 {
			return nil, func() {}, nil
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
