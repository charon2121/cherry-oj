package flow

import (
	"fmt"
	"strings"
	"unicode/utf8"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/judge/checker"
	"cherry-oj/judge-engine/internal/judge/testcase"
)

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
