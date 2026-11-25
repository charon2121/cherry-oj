package main

import (
	"context"
	"crypto/sha256"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strings"
	"unicode/utf8"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/language"
)

const jobInputMaxBytes = 8 << 20
const jobReportMaxBytes = 1 << 20

var languageIDPattern = regexp.MustCompile("^[a-z][a-z0-9-]{0,31}$")
var digestPattern = regexp.MustCompile("^[a-f0-9]{64}$")

type runnerInput struct {
	SchemaVersion   int              `json:"schemaVersion"`
	StatementDigest string           `json:"statementDigest"`
	GeneratorSource string           `json:"generatorSource"`
	ValidatorSource string           `json:"validatorSource"`
	OracleSource    string           `json:"oracleSource"`
	AnswerLanguage  string           `json:"answerLanguageId"`
	Cases           []runnerCase     `json:"cases"`
	Languages       []runnerLanguage `json:"languages"`
}

type runnerCase struct {
	Name           string  `json:"name"`
	GeneratorInput *string `json:"generatorInput"`
	Oracle         *bool   `json:"oracle"`
	ExpectedOutput *string `json:"expectedOutput"`
	SampleInput    *string `json:"sampleInput"`
}

type runnerProgram struct {
	Source         string  `json:"source"`
	SolutionSource *string `json:"solutionSource"`
}

type runnerWrong struct {
	Program         runnerProgram `json:"program"`
	WitnessCaseName string        `json:"witnessCaseName"`
}

type runnerLanguage struct {
	LanguageID     string               `json:"languageId"`
	Limits         contract.JudgeLimits `json:"limits"`
	Reference      runnerProgram        `json:"reference"`
	Starter        *runnerProgram       `json:"starter"`
	WrongSolutions []runnerWrong        `json:"wrongSolutions"`
}

type languageResult struct {
	LanguageID      string           `json:"languageId"`
	Status          string           `json:"status"`
	Verdict         contract.Verdict `json:"verdict,omitempty"`
	StarterVerdict  contract.Verdict `json:"starterVerdict,omitempty"`
	ToolchainDigest string           `json:"toolchainDigest,omitempty"`
	MaxCPUNs        int64            `json:"maxCpuNs,omitempty"`
	MaxMemoryBytes  int64            `json:"maxMemoryBytes,omitempty"`
	Detail          string           `json:"detail,omitempty"`
}

type runnerProgress struct {
	SchemaVersion int    `json:"schemaVersion"`
	Stage         string `json:"stage"`
}

type runnerResult struct {
	runnerProgress
	Status         string           `json:"status"`
	TestDataDigest string           `json:"testDataDigest,omitempty"`
	TestcaseCount  int              `json:"testcaseCount,omitempty"`
	Languages      []languageResult `json:"languages"`
	Detail         string           `json:"detail,omitempty"`
}

func runJob(ctx context.Context, args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet("problem-build", flag.ContinueOnError)
	flags.SetOutput(stderr)
	inputPath := flags.String("job", "", "冻结的构建输入")
	output := flags.String("output", "", "新的私有产物目录")
	judgeURL := flags.String("judge-url", "", "原生节点回环地址")
	toolchainRoot := flags.String("toolchain-root", "", "原生工具链清单目录")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *inputPath == "" || *output == "" || *toolchainRoot == "" || flags.NArg() != 0 {
		return fmt.Errorf("任务模式需要 -job、-output、-judge-url 和 -toolchain-root")
	}
	if err := loopbackURL(*judgeURL); err != nil {
		return err
	}
	if err := os.Mkdir(*output, 0o700); err != nil {
		return err
	}
	report := buildReport{results: []languageResult{}, progress: func(stage string) error {
		return writeJobJSON(*output, "progress.json", runnerProgress{1, stage})
	}}
	if err := report.mark("INPUT"); err != nil {
		return err
	}
	data, err := readText(*inputPath, jobInputMaxBytes, false)
	if err == nil {
		var wire runnerInput
		decoder := json.NewDecoder(strings.NewReader(data.text))
		decoder.DisallowUnknownFields()
		err = decoder.Decode(&wire)
		if err == nil {
			if _, tail := decoder.Token(); tail != io.EOF {
				err = errors.New("构建输入含额外内容")
			} else {
				report.input, err = wire.prepare(*toolchainRoot)
			}
		}
	}
	if err == nil {
		ctx, cancel := context.WithTimeout(ctx, buildTimeout)
		defer cancel()
		judge := newJudgeClient(*judgeURL)
		defer judge.client.CloseIdleConnections()
		err = build(ctx, judge, &report, *output)
	}
	for _, target := range report.input.languages {
		if !slices.ContainsFunc(report.results, func(item languageResult) bool { return item.LanguageID == target.id }) {
			report.results = append(report.results, languageResult{LanguageID: target.id, Status: "NOT_RUN", ToolchainDigest: target.manifest.sha256})
		}
	}
	result := runnerResult{runnerProgress: runnerProgress{1, report.stage}, Status: "FAILED", Languages: report.results}
	if err == nil {
		result.Status, result.TestDataDigest, result.TestcaseCount = "SUCCEEDED", report.digest, len(report.specs)
	} else {
		report.failure = err.Error()
		result.Detail = truncateText(report.failure, 2048)
	}
	for i := range result.Languages {
		result.Languages[i].Detail = truncateText(result.Languages[i].Detail, 2048)
	}
	if writeErr := writeReport(*output, report); writeErr != nil {
		return errors.Join(err, writeErr, removeArchive(*output))
	}
	if writeErr := writeJobJSON(*output, "result.json", result); writeErr != nil {
		return errors.Join(err, writeErr, removeArchive(*output))
	}
	return err
}

func (wire runnerInput) prepare(root string) (buildInput, error) {
	input := buildInput{
		toolLimits:     contract.JudgeLimits{CPUNs: 1_000_000_000, ClockNs: 3_000_000_000, MemoryBytes: 256 << 20},
		statement:      sourceFile{path: "题面快照", sha256: wire.StatementDigest},
		answerLanguage: wire.AnswerLanguage,
	}
	if wire.SchemaVersion != 1 || !digestPattern.MatchString(wire.StatementDigest) ||
		len(wire.Cases) < 1 || len(wire.Cases) > maxBuildCases || len(wire.Languages) < 1 || len(wire.Languages) > 4 {
		return input, errors.New("构建输入版本、题面指纹、测试点或语言数量无效")
	}
	for _, item := range []struct {
		name, text string
		target     *sourceFile
	}{
		{"生成器", wire.GeneratorSource, &input.generator}, {"校验器", wire.ValidatorSource, &input.validator},
		{"独立核对解", wire.OracleSource, &input.oracle},
	} {
		if !validSource(item.text) {
			return input, fmt.Errorf("%s为空或超过源码大小上限", item.name)
		}
		*item.target = snapshotSource(item.name, item.text)
	}
	manifest, err := fingerprintFile(filepath.Join(root, "cpp", "manifest.json"))
	if err != nil {
		return input, fmt.Errorf("C++ 出题工具链不可用：%w", err)
	}
	input.manifest = manifest
	seenCases := map[string]bool{}
	var samples, oracle bool
	for _, item := range wire.Cases {
		if !caseName.MatchString(item.Name) || seenCases[item.Name] || item.Oracle == nil || item.GeneratorInput == nil ||
			len(*item.GeneratorInput) > maxTrialBytes || !*item.Oracle && item.ExpectedOutput == nil {
			return input, fmt.Errorf("测试点 %q 缺少生成输入或独立核对依据，或名字无效", item.Name)
		}
		seenCases[item.Name] = true
		c := buildCase{name: item.Name, recipe: snapshotSource(item.Name, *item.GeneratorInput), oracle: *item.Oracle}
		oracle = oracle || c.oracle
		if item.ExpectedOutput != nil {
			if !validSource(*item.ExpectedOutput) {
				return input, fmt.Errorf("测试点 %q 的期望输出无效", item.Name)
			}
			expected := snapshotSource(item.Name, *item.ExpectedOutput)
			c.expected = &expected
		}
		if item.SampleInput != nil {
			if c.expected == nil || len(*item.SampleInput) > maxTrialBytes {
				return input, fmt.Errorf("测试点 %q 的样例无效", item.Name)
			}
			sample := snapshotSource(item.Name, *item.SampleInput)
			c.sample, samples = &sample, true
		}
		input.cases = append(input.cases, c)
	}
	if !samples || !oracle {
		return input, errors.New("缺少公开样例或独立解核对点")
	}
	slices.SortFunc(input.cases, func(a, b buildCase) int { return strings.Compare(a.name, b.name) })

	seenLanguages := map[string]bool{}
	var core *bool
	for _, item := range wire.Languages {
		id := item.LanguageID
		lang, known := language.Get(id)
		coreMode := item.Reference.SolutionSource != nil
		if !languageIDPattern.MatchString(id) || !known || seenLanguages[id] || (core != nil && *core != coreMode) ||
			(coreMode && (!lang.SupportsSolution() || item.Starter == nil)) || (!coreMode && item.Starter != nil) ||
			item.Limits.Validate() != nil || item.Limits.ClockNs <= 0 || item.Limits.ClockNs > 120_000_000_000 ||
			item.Limits.CPUNs > 10_000_000_000 || item.Limits.MemoryBytes > 1<<30 ||
			len(item.WrongSolutions) < 1 || len(item.WrongSolutions) > 16 {
			return input, fmt.Errorf("作答语言 %q 的程序、限制或错误解数量不合法", id)
		}
		seenLanguages[id], core = true, &coreMode
		target := languageBuild{id: id, limits: item.Limits, manifest: input.manifest}
		if id != "cpp" {
			target.manifest, err = fingerprintFile(filepath.Join(root, id, "manifest.json"))
			if err != nil {
				return input, fmt.Errorf("作答语言 %s 的工具链不可用：%w", id, err)
			}
		}
		target.reference, err = item.Reference.program("参考解", coreMode)
		if err != nil {
			return input, err
		}
		if id == wire.AnswerLanguage && id == "cpp" && !coreMode && target.reference.source.sha256 == input.oracle.sha256 {
			return input, fmt.Errorf("参考解和独立核对解的源码相同")
		}
		if item.Starter != nil {
			starter, err := item.Starter.program("起始代码", true)
			if err != nil {
				return input, err
			}
			target.starter = &starter
		}
		for i, wrong := range item.WrongSolutions {
			if !seenCases[wrong.WitnessCaseName] {
				return input, fmt.Errorf("错误解指定的测试点 %q 不存在", wrong.WitnessCaseName)
			}
			p, err := wrong.Program.program(fmt.Sprintf("错误解 %d", i+1), coreMode)
			if err != nil {
				return input, err
			}
			target.wrong = append(target.wrong, wrongSolution{caseName: wrong.WitnessCaseName, program: p})
		}
		input.languages = append(input.languages, target)
	}
	if !seenLanguages[input.answerLanguage] {
		return input, fmt.Errorf("未配置答案语言 %q", input.answerLanguage)
	}
	return input, nil
}

func (wire runnerProgram) program(label string, core bool) (program, error) {
	var p program
	if !validSource(wire.Source) || core != (wire.SolutionSource != nil) {
		return p, fmt.Errorf("%s源码或文件数无效", label)
	}
	p.source = snapshotSource(label, wire.Source)
	if core {
		if !validSource(*wire.SolutionSource) {
			return p, fmt.Errorf("%s用户文件无效", label)
		}
		p.source.path = "题目驱动"
		solution := snapshotSource(label, *wire.SolutionSource)
		p.solution = &solution
	}
	return p, nil
}

func validSource(value string) bool { return len(value) > 0 && len(value) <= maxSourceBytes }

func snapshotSource(label, value string) sourceFile {
	return sourceFile{path: label, text: value, sha256: fmt.Sprintf("%x", sha256.Sum256([]byte(value)))}
}

func writeJobJSON(dir, name string, data any) error {
	file, err := os.CreateTemp(dir, ".json-*")
	if err != nil {
		return err
	}
	defer os.Remove(file.Name())
	if err := errors.Join(json.NewEncoder(file).Encode(data), file.Close()); err != nil {
		return err
	}
	return os.Rename(file.Name(), filepath.Join(dir, name))
}

func truncateText(value string, limit int) string {
	if len(value) <= limit {
		return value
	}
	for !utf8.RuneStart(value[limit]) {
		limit--
	}
	return value[:limit]
}
