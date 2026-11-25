package main

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"strings"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/testcase"
)

type runTrial func(stage string, target languageBuild, program program, specs []contract.TestcaseSpec) (contract.JudgeResult, error)

func build(ctx context.Context, judge judgeClient, report *buildReport, output string) error {
	input := report.input
	run := func(stage string, target languageBuild, program program, specs []contract.TestcaseSpec) (contract.JudgeResult, error) {
		result, err := judge.trial(ctx, target.id, target.limits, program, specs)
		summary := result
		summary.TestcaseResults = slices.Clone(result.TestcaseResults)
		for i := range summary.TestcaseResults {
			summary.TestcaseResults[i].Output = nil
			summary.TestcaseResults[i].Diff = nil
		}
		report.runs = append(report.runs, trialRecord{stage: stage, result: summary})
		if err != nil {
			return result, fmt.Errorf("%s：%w", stage, err)
		}
		return result, nil
	}
	answerIndex := slices.IndexFunc(input.languages, func(target languageBuild) bool {
		return target.id == input.answerLanguage
	})
	if answerIndex < 0 {
		return fmt.Errorf("未配置答案语言 %q", input.answerLanguage)
	}
	answer := input.languages[answerIndex]
	tools := languageBuild{id: "cpp", limits: input.toolLimits}

	recipes := make([]contract.TestcaseSpec, 0, len(input.cases))
	for _, c := range input.cases {
		recipes = append(recipes, contract.TestcaseSpec{Name: c.name, Input: c.recipe.text})
	}
	if err := report.mark("GENERATOR"); err != nil {
		return err
	}
	generated, err := run("生成输入", tools, program{source: input.generator}, recipes)
	if err != nil {
		return err
	}
	generatedInputs, err := report.fullOutputs("生成输入", generated, recipes)
	if err != nil {
		return err
	}
	specs := make([]contract.TestcaseSpec, 0, len(input.cases))
	for i, c := range input.cases {
		text := generatedInputs[i]
		if c.sample != nil && c.sample.text != text {
			report.evidence = fmt.Sprintf("生成的输入=%q\n已确认的样例输入=%q\n", text, c.sample.text)
			return fmt.Errorf("样例核对：测试点 %q 的输入与公开样例不一致", c.name)
		}
		specs = append(specs, contract.TestcaseSpec{Name: c.name, Input: text})
	}
	if err := report.mark("VALIDATOR"); err != nil {
		return err
	}
	validated, err := run("校验输入", tools, program{source: input.validator}, specs)
	if err != nil {
		return err
	}
	if err := report.requireVerdict("校验输入", validated, specs, contract.VerdictRAN); err != nil {
		return err
	}
	if err := report.mark("ANSWERS"); err != nil {
		return err
	}
	reference, err := run("生成答案", answer, answer.reference, specs)
	if err != nil {
		return err
	}
	answers, err := report.fullOutputs("生成答案", reference, specs)
	if err != nil {
		return err
	}

	oracleSpecs := make([]contract.TestcaseSpec, 0, len(specs))
	for i, c := range input.cases {
		if answers[i] == "" {
			report.evidence = fmt.Sprintf("输入=%q\n", specs[i].Input)
			return fmt.Errorf("生成答案：测试点 %q 的标准输出为空，试运行 checker 需要非空期望输出", c.name)
		}
		if c.expected != nil && c.expected.text != answers[i] {
			return report.answerMismatch(specs[i], answers[i], "独立期望输出", c.expected.text)
		}
		if c.oracle {
			oracleSpecs = append(oracleSpecs, specs[i])
		}
	}
	if err := report.mark("ORACLE"); err != nil {
		return err
	}
	checked, err := run("运行独立核对解", tools, program{source: input.oracle}, oracleSpecs)
	if err != nil {
		return err
	}
	independent, err := report.fullOutputs("运行独立核对解", checked, oracleSpecs)
	if err != nil {
		return err
	}
	checkedIndex := 0
	for i, c := range input.cases {
		if c.oracle {
			if independent[checkedIndex] != answers[i] {
				return report.answerMismatch(specs[i], answers[i], "独立核对解", independent[checkedIndex])
			}
			checkedIndex++
		}
		specs[i].Expected = answers[i]
	}
	report.specs = specs

	samples := make([]contract.TestcaseSpec, 0, len(specs))
	for i, c := range input.cases {
		if c.sample != nil {
			samples = append(samples, contract.TestcaseSpec{Name: c.name, Input: specs[i].Input})
		}
	}
	var languageErrors []error
	if err := report.mark("LANGUAGES"); err != nil {
		return err
	}
	for _, target := range input.languages {
		if ctx.Err() != nil {
			languageErrors = append(languageErrors, ctx.Err())
			break
		}
		outcome := languageResult{LanguageID: target.id, Status: "PASSED", ToolchainDigest: target.manifest.sha256}
		if err := verifyLanguage(run, report, target, specs, samples, &outcome); err != nil {
			outcome.Status, outcome.Detail = "FAILED", err.Error()
			languageErrors = append(languageErrors, err)
		}
		report.results = append(report.results, outcome)
	}
	if err := errors.Join(languageErrors...); err != nil {
		return err
	}
	if err := ctx.Err(); err != nil {
		return fmt.Errorf("导出 ZIP：%w", err)
	}
	if err := report.mark("EXPORT"); err != nil {
		return err
	}
	if err := writeArchive(output, specs); err != nil {
		return fmt.Errorf("导出 ZIP：%w", err)
	}
	report.digest = testcase.DigestSpecs(specs)
	return nil
}

func (r *buildReport) mark(stage string) error {
	r.stage = stage
	if r.progress != nil {
		return r.progress(stage)
	}
	return nil
}

func verifyLanguage(run runTrial, report *buildReport, target languageBuild, specs, samples []contract.TestcaseSpec, outcome *languageResult) error {
	referenceStage := "checker 验证参考解 [" + target.id + "]"
	correct, err := run(referenceStage, target, target.reference, specs)
	outcome.Verdict, outcome.MaxCPUNs, outcome.MaxMemoryBytes = correct.Verdict, correct.CPUNs, correct.MemoryBytes
	if err != nil {
		return err
	}
	if err := report.requireVerdict(referenceStage, correct, specs, contract.VerdictAC); err != nil {
		return err
	}
	if target.starter != nil {
		stage := "运行 CORE 起始代码 [" + target.id + "]"
		starter, err := run(stage, target, *target.starter, samples)
		outcome.StarterVerdict = starter.Verdict
		if err != nil {
			return err
		}
		if err := report.requireVerdict(stage, starter, samples, contract.VerdictRAN); err != nil {
			return err
		}
	}
	for _, wrong := range target.wrong {
		stage := fmt.Sprintf("checker 验证错误解 [%s] %q（指定点 %s）", target.id, wrong.program.submission().path, wrong.caseName)
		result, err := run(stage, target, wrong.program, specs)
		if err != nil {
			return err
		}
		for i, row := range result.TestcaseResults {
			if row.Verdict != contract.VerdictAC && row.Verdict != contract.VerdictWA {
				return report.caseFailure(stage, specs[i], row.Verdict, "期望 AC 或 WA")
			}
			if row.Name == wrong.caseName && row.Verdict != contract.VerdictWA {
				return report.caseFailure(stage, specs[i], row.Verdict, "需在指定点得到 WA")
			}
		}
		if result.Verdict != contract.VerdictWA {
			return fmt.Errorf("%s：总体结论为 %s，期望 WA", stage, result.Verdict)
		}
	}
	return nil
}

func (r *buildReport) requireVerdict(stage string, result contract.JudgeResult, specs []contract.TestcaseSpec, want contract.Verdict) error {
	for i, row := range result.TestcaseResults {
		if row.Verdict != want {
			return r.caseFailure(stage, specs[i], row.Verdict, "期望 "+string(want))
		}
	}
	if result.Verdict != want {
		return fmt.Errorf("%s：总体结论为 %s，期望 %s", stage, result.Verdict, want)
	}
	return nil
}

func (r *buildReport) fullOutputs(stage string, result contract.JudgeResult, specs []contract.TestcaseSpec) ([]string, error) {
	if err := r.requireVerdict(stage, result, specs, contract.VerdictRAN); err != nil {
		return nil, err
	}
	outputs := make([]string, 0, len(specs))
	for i, row := range result.TestcaseResults {
		o := row.Output
		// JSON 会替换非法 UTF-8。字节数不匹配时，也不能用收到的文本生成数据。
		if o == nil || o.Truncated || o.Bytes != int64(len(o.Excerpt)) || o.Bytes > maxTrialBytes {
			return nil, r.caseFailure(stage, specs[i], row.Verdict, "需取得完整 UTF-8 输出，检查 judge 摘录与输出上限")
		}
		outputs = append(outputs, o.Excerpt)
	}
	return outputs, nil
}

func (r *buildReport) caseFailure(stage string, c contract.TestcaseSpec, verdict contract.Verdict, reason string) error {
	var evidence strings.Builder
	fmt.Fprintf(&evidence, "%s %s 输入=%q\n", stage, c.Name, c.Input)
	if c.Expected != "" {
		fmt.Fprintf(&evidence, "期望输出=%q\n", c.Expected)
	}
	r.evidence += evidence.String()
	return fmt.Errorf("%s：测试点 %q 返回 %s，%s", stage, c.Name, verdict, reason)
}

func (r *buildReport) answerMismatch(c contract.TestcaseSpec, reference, method, checked string) error {
	r.evidence = fmt.Sprintf("输入=%q\n参考输出=%q\n%s=%q\n", c.Input, reference, method, checked)
	return fmt.Errorf("答案核对：测试点 %q 的参考输出与%s不一致", c.Name, method)
}
