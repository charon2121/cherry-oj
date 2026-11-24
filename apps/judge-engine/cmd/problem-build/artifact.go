package main

import (
	"archive/zip"
	"crypto/sha256"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"cherry-oj/judge-engine/internal/contract"
)

const archiveName = "testdata.zip"

type buildReport struct {
	input    buildInput
	runs     []trialRecord
	specs    []contract.TestcaseSpec
	digest   string
	failure  string
	evidence string
}

type trialRecord struct {
	stage  string
	result contract.JudgeResult
}

func writeReport(dir string, report buildReport) error {
	var out strings.Builder
	if report.failure == "" {
		fmt.Fprintf(&out, "验题通过\n数据指纹=%s\n", report.digest)
	} else {
		fmt.Fprintf(&out, "验题失败\n失败原因=%q\n%s", report.failure, report.evidence)
	}
	limits := report.input.options.limits
	fmt.Fprintf(&out, "\nC++ ACM 每点上限 cpuNs=%d clockNs=%d memoryBytes=%d\n", limits.CPUNs, limits.ClockNs, limits.MemoryBytes)
	for _, file := range []struct {
		label string
		file  sourceFile
	}{
		{"题面", report.input.statement}, {"运行者提供的 C++ 工具链清单", report.input.manifest},
		{"生成器", report.input.generator}, {"校验器", report.input.validator},
		{"参考解", report.input.reference}, {"独立核对解", report.input.oracle},
	} {
		fmt.Fprintf(&out, "%s %q sha256=%s\n", file.label, file.file.path, file.file.sha256)
	}
	fmt.Fprintln(&out, "\n测试点（上传及判题顺序）")
	for i, c := range report.input.cases {
		fmt.Fprintf(&out, "%d. %s 生成参数 %q sha256=%s stdin=%q\n", i+1, c.name, c.recipe.path, c.recipe.sha256, c.recipe.text)
		if c.sample != nil {
			fmt.Fprintf(&out, "   公开样例输入 %q sha256=%s\n", c.sample.path, c.sample.sha256)
		}
		if c.expected != nil {
			fmt.Fprintf(&out, "   独立期望输出 %q sha256=%s\n", c.expected.path, c.expected.sha256)
		}
		if c.oracle {
			fmt.Fprintln(&out, "   使用独立核对解")
		}
		if i < len(report.specs) {
			spec := report.specs[i]
			fmt.Fprintf(&out, "   输入 sha256=%x 输出 sha256=%x\n", sha256.Sum256([]byte(spec.Input)), sha256.Sum256([]byte(spec.Expected)))
		}
	}
	for _, wrong := range report.input.wrong {
		fmt.Fprintf(&out, "错误解 %q sha256=%s 指定 WA 点=%s\n", wrong.source.path, wrong.source.sha256, wrong.caseName)
	}
	for _, run := range report.runs {
		result := run.result
		fmt.Fprintf(&out, "\n%s verdict=%s cpuNs=%d memoryBytes=%d\n", run.stage, result.Verdict, result.CPUNs, result.MemoryBytes)
		if result.Message != "" {
			fmt.Fprintf(&out, "  message=%q\n", result.Message)
		}
		for _, row := range result.TestcaseResults {
			fmt.Fprintf(&out, "  %q verdict=%s cpuNs=%d memoryBytes=%d\n", row.Name, row.Verdict, row.CPUNs, row.MemoryBytes)
			if row.Message != "" {
				fmt.Fprintf(&out, "    message=%q\n", row.Message)
			}
			if row.Stderr != nil && row.Stderr.Excerpt != "" {
				fmt.Fprintf(&out, "    stderr=%q truncated=%t\n", row.Stderr.Excerpt, row.Stderr.Truncated)
			}
		}
	}
	if err := os.WriteFile(filepath.Join(dir, "report.txt"), []byte(out.String()), 0o600); err != nil {
		return fmt.Errorf("写入报告：%w", err)
	}
	return nil
}

func writeArchive(dir string, specs []contract.TestcaseSpec) error {
	file, err := os.CreateTemp(dir, ".testdata-*")
	if err != nil {
		return err
	}
	defer os.Remove(file.Name())
	archive := zip.NewWriter(file)
	err = writeEntries(archive, specs)
	if err = errors.Join(err, archive.Close(), file.Close()); err != nil {
		return err
	}
	return os.Rename(file.Name(), filepath.Join(dir, archiveName))
}

func writeEntries(archive *zip.Writer, specs []contract.TestcaseSpec) error {
	for _, c := range specs {
		for _, f := range []struct{ suffix, text string }{{".in", c.Input}, {".out", c.Expected}} {
			// 上传端限制压缩比，重复值构造点也要能上传。
			entry, err := archive.CreateHeader(&zip.FileHeader{Name: c.Name + f.suffix, Method: zip.Store})
			if err != nil {
				return err
			}
			if _, err := io.WriteString(entry, f.text); err != nil {
				return err
			}
		}
	}
	return nil
}

func removeArchive(dir string) error {
	err := os.Remove(filepath.Join(dir, archiveName))
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	return err
}
