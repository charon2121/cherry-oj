package main

import (
	"crypto/sha256"
	"flag"
	"fmt"
	"io"
	"net/netip"
	"net/url"
	"os"
	"regexp"
	"slices"
	"strings"
	"unicode/utf8"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/config"
)

const (
	maxSourceBytes = 64 << 10
	maxTrialBytes  = 64 << 10
	maxBuildCases  = 64
)

// 字母开头的名字在上传端和本工具中都按字典序排列，数据指纹因而包含相同的判题顺序。
var caseName = regexp.MustCompile(`^[a-z][a-z0-9-]{0,62}$`)

type fileAssignments map[string]string

func (f fileAssignments) String() string { return "" }

func (f fileAssignments) Set(value string) error {
	name, path, err := parseAssignment(value)
	if err != nil {
		return err
	}
	if _, exists := f[name]; exists {
		return fmt.Errorf("重复的测试点 %q", name)
	}
	f[name] = path
	return nil
}

func parseAssignment(value string) (string, string, error) {
	name, path, found := strings.Cut(value, "=")
	if !found || !caseName.MatchString(name) || path == "" {
		return "", "", fmt.Errorf("需要 name=path，name 必须匹配 %s", caseName)
	}
	return name, path, nil
}

type wrongAssignment struct {
	caseName, sourcePath string
}

type buildOptions struct {
	judgeURL          string
	output            string
	toolchainManifest string
	statement         string
	generator         string
	validator         string
	reference         string
	oracle            string
	cases             fileAssignments
	expected          fileAssignments
	samples           fileAssignments
	wrong             []wrongAssignment
	oracleCases       map[string]bool
	limits            contract.JudgeLimits
}

func parseOptions(args []string, stderr io.Writer) (buildOptions, error) {
	options := buildOptions{
		cases: make(fileAssignments), expected: make(fileAssignments), samples: make(fileAssignments),
		oracleCases: make(map[string]bool),
	}
	flags := flag.NewFlagSet("problem-build", flag.ContinueOnError)
	flags.SetOutput(stderr)
	flags.StringVar(&options.judgeURL, "judge-url", "http://127.0.0.1:15051", "受信原生判题节点的回环 HTTP 地址")
	flags.StringVar(&options.output, "output", "", "新的私有产物目录")
	flags.StringVar(&options.toolchainManifest, "toolchain-manifest", "", "原生节点当前 C++ 工具链的 manifest.json")
	flags.StringVar(&options.statement, "statement", "", "题面与约束的 Markdown 文件")
	flags.StringVar(&options.generator, "generator", "", "C++ 生成器源码")
	flags.StringVar(&options.validator, "validator", "", "C++ 输入校验器源码，输入合法时退出码为 0")
	flags.StringVar(&options.reference, "reference", "", "高效 C++ 参考解源码")
	flags.StringVar(&options.oracle, "oracle", "", "独立 C++ 核对解源码")
	flags.Var(options.cases, "case", "name=path，将该文件原样作为生成器 stdin，可重复")
	flags.Var(options.expected, "expected", "name=path，独立确定的期望输出，例如样例或构造点，可重复")
	flags.Var(options.samples, "sample", "name=path，核对公开样例的输入原文，须同时指定 -expected")
	flags.Func("wrong", "name=path，必须在指定测试点判 WA 的 C++ 错误解，可重复", func(value string) error {
		name, path, err := parseAssignment(value)
		if err != nil {
			return err
		}
		options.wrong = append(options.wrong, wrongAssignment{caseName: name, sourcePath: path})
		return nil
	})
	flags.Func("oracle-case", "需要用独立解核对的测试点，可重复", func(name string) error {
		if !caseName.MatchString(name) || options.oracleCases[name] {
			return fmt.Errorf("无效或重复的测试点 %q", name)
		}
		options.oracleCases[name] = true
		return nil
	})
	flags.Int64Var(&options.limits.CPUNs, "cpu-ns", 1_000_000_000, "每点 CPU 上限，单位 ns")
	flags.Int64Var(&options.limits.ClockNs, "clock-ns", 3_000_000_000, "每点墙钟上限，单位 ns")
	flags.Int64Var(&options.limits.MemoryBytes, "memory-bytes", 256<<20, "每点内存上限，单位 bytes")
	if err := flags.Parse(args); err != nil {
		return options, err
	}
	if flags.NArg() != 0 {
		return options, fmt.Errorf("未识别的参数 %q", flags.Arg(0))
	}
	for _, flag := range []struct{ name, path string }{
		{"output", options.output}, {"toolchain-manifest", options.toolchainManifest},
		{"statement", options.statement}, {"generator", options.generator}, {"validator", options.validator},
		{"reference", options.reference}, {"oracle", options.oracle},
	} {
		if flag.path == "" {
			return options, fmt.Errorf("缺少 -%s", flag.name)
		}
	}
	if len(options.cases) == 0 || len(options.cases) > maxBuildCases || len(options.wrong) == 0 || len(options.wrong) > maxBuildCases {
		return options, fmt.Errorf("测试点与错误解分别需要 1 至 %d 项", maxBuildCases)
	}
	if len(options.samples) == 0 || len(options.oracleCases) == 0 {
		return options, fmt.Errorf("需要至少一个 -sample 和一个 -oracle-case")
	}
	if err := options.limits.Validate(); err != nil {
		return options, err
	}
	if options.limits.ClockNs <= 0 || options.limits.ClockNs > config.MaxClockNs {
		return options, fmt.Errorf("-clock-ns 必须为 1 至 %d", config.MaxClockNs)
	}
	if err := loopbackURL(options.judgeURL); err != nil {
		return options, err
	}
	return options, nil
}

func loopbackURL(raw string) error {
	u, err := url.Parse(raw)
	if err == nil {
		ip, ipErr := netip.ParseAddr(u.Hostname())
		if u.Scheme == "http" && ipErr == nil && ip.IsLoopback() && u.User == nil && u.Port() != "" &&
			(u.Path == "" || u.Path == "/") && u.RawPath == "" && u.RawQuery == "" && !u.ForceQuery && u.Fragment == "" {
			return nil
		}
	}
	return fmt.Errorf("-judge-url 必须是带端口的回环 HTTP 地址")
}

type sourceFile struct {
	path, text, sha256 string
}

type buildCase struct {
	name     string
	recipe   sourceFile
	expected *sourceFile
	sample   *sourceFile
	oracle   bool
}

type wrongSolution struct {
	caseName string
	source   sourceFile
}

type buildInput struct {
	options                                                      buildOptions
	statement, manifest, generator, validator, reference, oracle sourceFile
	cases                                                        []buildCase
	wrong                                                        []wrongSolution
}

func loadBuild(options buildOptions) (buildInput, error) {
	input := buildInput{options: options}
	for _, item := range []struct {
		path string
		dest *sourceFile
	}{
		{options.statement, &input.statement},
		{options.generator, &input.generator}, {options.validator, &input.validator},
		{options.reference, &input.reference}, {options.oracle, &input.oracle},
	} {
		file, err := readText(item.path, maxSourceBytes, false)
		if err != nil {
			return input, err
		}
		*item.dest = file
	}
	if input.reference.sha256 == input.oracle.sha256 {
		return input, fmt.Errorf("参考解和独立核对解的源码相同")
	}
	manifest, err := fingerprintFile(options.toolchainManifest)
	if err != nil {
		return input, err
	}
	input.manifest = manifest
	for _, assignments := range []fileAssignments{options.expected, options.samples} {
		for name := range assignments {
			if _, ok := options.cases[name]; !ok {
				return input, fmt.Errorf("没有配置测试点 %q", name)
			}
		}
	}
	for name := range options.oracleCases {
		if _, ok := options.cases[name]; !ok {
			return input, fmt.Errorf("没有配置独立解核对点 %q", name)
		}
	}
	for _, name := range sortedNames(options.cases) {
		c := buildCase{name: name, oracle: options.oracleCases[name]}
		recipe, err := readText(options.cases[name], maxTrialBytes, true)
		if err != nil {
			return input, err
		}
		c.recipe = recipe
		if path, ok := options.expected[name]; ok {
			expected, err := readText(path, maxTrialBytes, false)
			if err != nil {
				return input, err
			}
			c.expected = &expected
		}
		if path, ok := options.samples[name]; ok {
			if c.expected == nil {
				return input, fmt.Errorf("样例 %q 缺少 -expected", name)
			}
			sample, err := readText(path, maxTrialBytes, true)
			if err != nil {
				return input, err
			}
			c.sample = &sample
		}
		if !c.oracle && c.expected == nil {
			return input, fmt.Errorf("测试点 %q 缺少独立核对依据", name)
		}
		input.cases = append(input.cases, c)
	}
	for _, witness := range options.wrong {
		if _, ok := options.cases[witness.caseName]; !ok {
			return input, fmt.Errorf("错误解 %q 的指定测试点 %q 未配置", witness.sourcePath, witness.caseName)
		}
		wrong, err := readText(witness.sourcePath, maxSourceBytes, false)
		if err != nil {
			return input, err
		}
		input.wrong = append(input.wrong, wrongSolution{caseName: witness.caseName, source: wrong})
	}
	return input, nil
}

func sortedNames(files fileAssignments) []string {
	names := make([]string, 0, len(files))
	for name := range files {
		names = append(names, name)
	}
	slices.Sort(names)
	return names
}

func readText(path string, limit int64, allowEmpty bool) (sourceFile, error) {
	file, err := openRegular(path)
	if err != nil {
		return sourceFile{}, err
	}
	defer file.Close()
	data, err := io.ReadAll(io.LimitReader(file, limit+1))
	if err != nil {
		return sourceFile{}, fmt.Errorf("读取 %s：%w", path, err)
	}
	if !allowEmpty && len(data) == 0 {
		return sourceFile{}, fmt.Errorf("%s 不能为空", path)
	}
	if int64(len(data)) > limit || !utf8.Valid(data) {
		return sourceFile{}, fmt.Errorf("%s 需要有效的 UTF-8 文本，大小上限 %d 字节", path, limit)
	}
	return sourceFile{path: path, text: string(data), sha256: fmt.Sprintf("%x", sha256.Sum256(data))}, nil
}

func fingerprintFile(path string) (sourceFile, error) {
	file, err := openRegular(path)
	if err != nil {
		return sourceFile{}, err
	}
	defer file.Close()
	hash := sha256.New()
	size, err := io.Copy(hash, file)
	if err != nil {
		return sourceFile{}, fmt.Errorf("读取工具链清单 %s：%w", path, err)
	}
	if size == 0 {
		return sourceFile{}, fmt.Errorf("工具链清单 %s 不能为空", path)
	}
	return sourceFile{path: path, sha256: fmt.Sprintf("%x", hash.Sum(nil))}, nil
}

func openRegular(path string) (*os.File, error) {
	info, err := os.Stat(path)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() {
		return nil, fmt.Errorf("%s 必须是普通文件", path)
	}
	return os.Open(path)
}
