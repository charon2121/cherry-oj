package flow

import (
	"context"
	"fmt"

	"cherry-oj/judge-engine/internal/contract"
)

// compile 返回编译产物 ref。解释型语言不编译，ref 为空；若判题应当提前结束，
// early 带着已经区分好 CE / SE 的结果。
func (j *judgment) compile(ctx context.Context) (ref string, early *contract.JudgeResult) {
	sb, cfg, lang, sourceRef := j.sandbox, j.config, j.language, j.sourceRef
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
