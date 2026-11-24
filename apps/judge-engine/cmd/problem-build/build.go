package main

import (
	"context"
	"fmt"
	"slices"
	"strings"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/testcase"
)

func build(ctx context.Context, judge judgeClient, report *buildReport, output string) error {
	input := report.input
	run := func(stage string, source sourceFile, specs []contract.TestcaseSpec) (contract.JudgeResult, error) {
		result, err := judge.trial(ctx, input.options.limits, source.text, specs)
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

	recipes := make([]contract.TestcaseSpec, 0, len(input.cases))
	for _, c := range input.cases {
		recipes = append(recipes, contract.TestcaseSpec{Name: c.name, Input: c.recipe.text})
	}
	generated, err := run("生成输入", input.generator, recipes)
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
	validated, err := run("校验输入", input.validator, specs)
	if err != nil {
		return err
	}
	if err := report.requireVerdict("校验输入", validated, specs, contract.VerdictRAN); err != nil {
		return err
	}
	reference, err := run("生成答案", input.reference, specs)
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
	checked, err := run("运行独立核对解", input.oracle, oracleSpecs)
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

	correct, err := run("checker 验证参考解", input.reference, specs)
	if err != nil {
		return err
	}
	if err := report.requireVerdict("checker 验证参考解", correct, specs, contract.VerdictAC); err != nil {
		return err
	}
	for _, wrong := range input.wrong {
		stage := fmt.Sprintf("checker 验证错误解 %q（指定点 %s）", wrong.source.path, wrong.caseName)
		result, err := run(stage, wrong.source, specs)
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
	if err := ctx.Err(); err != nil {
		return fmt.Errorf("导出 ZIP：%w", err)
	}
	if err := writeArchive(output, specs); err != nil {
		return fmt.Errorf("导出 ZIP：%w", err)
	}
	report.digest = testcase.DigestSpecs(specs)
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
	fmt.Fprintf(&evidence, "输入=%q\n", c.Input)
	if c.Expected != "" {
		fmt.Fprintf(&evidence, "期望输出=%q\n", c.Expected)
	}
	r.evidence = evidence.String()
	return fmt.Errorf("%s：测试点 %q 返回 %s，%s", stage, c.Name, verdict, reason)
}

func (r *buildReport) answerMismatch(c contract.TestcaseSpec, reference, method, checked string) error {
	r.evidence = fmt.Sprintf("输入=%q\n参考输出=%q\n%s=%q\n", c.Input, reference, method, checked)
	return fmt.Errorf("答案核对：测试点 %q 的参考输出与%s不一致", c.Name, method)
}
