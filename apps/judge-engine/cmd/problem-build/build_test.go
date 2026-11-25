package main

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
)

func TestBuildGatesBeforeExport(t *testing.T) {
	for _, test := range []struct{ stage, failure string }{
		{},
		{"validator", `校验输入：测试点 \"boundary\" 返回 RE`},
		{"oracle", "参考输出与独立核对解不一致"},
		{"wrong", "需在指定点得到 WA"},
		{"truncated", "需取得完整 UTF-8 输出"},
		{"system", "返回 SE"},
		{"core", ""},
		{"core-starter", "运行 CORE 起始代码 [pypy]"},
		{"multi", ""},
		{"multi-mismatch", "checker 验证参考解 [pypy]"},
	} {
		t.Run(test.stage, func(t *testing.T) {
			root := t.TempDir()
			file := func(name, content string) string {
				t.Helper()
				path := filepath.Join(root, name)
				if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
					t.Fatal(err)
				}
				return path
			}
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				var req contract.JudgeRequest
				if err := json.NewDecoder(r.Body).Decode(&req); err != nil || r.URL.Path != "/judge" ||
					req.Mode != contract.ModeTrial {
					t.Errorf("invalid trial request: %v %#v", err, req)
					w.WriteHeader(http.StatusBadRequest)
					return
				}
				source := req.Source
				if req.Source == "driver" {
					if req.LanguageID != "pypy" || !strings.HasPrefix(req.SolutionSource, "prefix\n") {
						t.Errorf("CORE trial did not keep language and source files: %#v", req)
						w.WriteHeader(http.StatusBadRequest)
						return
					}
					source = strings.TrimPrefix(req.SolutionSource, "prefix\n")
				} else {
					expectedLanguage := "cpp"
					if source == "second-reference" || source == "second-wrong" {
						expectedLanguage = "pypy"
					}
					if req.LanguageID != expectedLanguage || req.SolutionSource != "" {
						t.Errorf("ACM trial had the wrong language or a CORE file: %#v", req)
						w.WriteHeader(http.StatusBadRequest)
						return
					}
				}
				if test.stage == "system" {
					_ = json.NewEncoder(w).Encode(contract.JudgeResult{Verdict: contract.VerdictSE, Message: "node unavailable"})
					return
				}
				result := contract.JudgeResult{Verdict: contract.VerdictAC}
				for i, c := range req.Testcases {
					row := contract.TestcaseResult{Idx: i + 1, Name: c.Name, Verdict: contract.VerdictRAN}
					output := "2\n"
					switch source {
					case "generator":
						output = c.Input
					case "validator":
						output = ""
						if test.stage == "validator" && c.Name == "boundary" {
							row.Verdict = contract.VerdictRE
						}
					case "oracle":
						if test.stage == "oracle" {
							output = "3\n"
						}
					case "reference":
						if c.Expected != "" {
							row.Verdict = contract.VerdictAC
						}
					case "second-reference":
						if c.Expected != "2\n" || len(req.Testcases) != 2 {
							t.Errorf("additional language received different answers: %#v", req.Testcases)
						}
						row.Verdict = contract.VerdictAC
						if test.stage == "multi-mismatch" && c.Name == "boundary" {
							row.Verdict = contract.VerdictWA
						}
					case "starter":
						if len(req.Testcases) != 1 || c.Name != "sample" || c.Expected != "" {
							t.Errorf("starter must only run public samples without comparison: %#v", req.Testcases)
						}
						if test.stage == "core-starter" {
							row.Verdict = contract.VerdictRE
						}
					case "wrong", "wrong-again", "second-wrong":
						row.Verdict = contract.VerdictAC
						if (test.stage == "wrong" && c.Name == "sample") || (test.stage != "wrong" && c.Name == "boundary") {
							row.Verdict = contract.VerdictWA
						}
					}
					if row.Verdict != contract.VerdictAC {
						row.Output = &contract.Output{Excerpt: output, Bytes: int64(len(output)), Truncated: test.stage == "truncated"}
					}
					if row.Verdict != contract.VerdictAC && (result.Verdict == contract.VerdictAC || row.Verdict != contract.VerdictRAN) {
						result.Verdict = row.Verdict
					}
					result.TestcaseResults = append(result.TestcaseResults, row)
				}
				_ = json.NewEncoder(w).Encode(result)
			}))
			defer server.Close()
			dir := filepath.Join(root, "out")
			var stdout, stderr bytes.Buffer
			args := []string{
				"-judge-url", server.URL, "-output", dir, "-toolchain-manifest", file("manifest", "{}"),
				"-statement", file("statement", "题面"), "-generator", file("generator", "generator"),
				"-validator", file("validator", "validator"), "-reference", file("reference", "reference"),
				"-oracle", file("oracle", "oracle"), "-case", "sample=" + file("sample", "1 1\n"),
				"-case", "boundary=" + file("boundary", "2 0\n"), "-oracle-case", "boundary",
				"-sample", "sample=" + filepath.Join(root, "sample"), "-expected", "sample=" + file("answer", "2\n"),
				"-wrong", "boundary=" + file("wrong", "wrong"),
				"-wrong", "boundary=" + file("wrong-again", "wrong-again"),
			}
			if strings.HasPrefix(test.stage, "core") {
				args = append(args,
					"-answer-language", "pypy", "-answer-toolchain-manifest", file("pypy-manifest", "{}"),
					"-driver", file("driver", "driver"), "-solution-prefix", file("prefix", "prefix\n"), "-starter", file("starter", "starter"))
			}
			var code int
			if strings.HasPrefix(test.stage, "multi") {
				options, err := parseOptions(args, &stderr)
				if err != nil {
					t.Fatal(err)
				}
				input, err := loadBuild(options)
				if err != nil {
					t.Fatal(err)
				}
				second := input.languages[0]
				second.id = "pypy"
				second.reference = program{source: sourceFile{text: "second-reference"}}
				second.wrong = []wrongSolution{{
					caseName: "boundary",
					program:  program{source: sourceFile{text: "second-wrong"}},
				}}
				input.languages = append(input.languages, second)
				if _, err := runBuild(t.Context(), options.judgeURL, dir, input); err != nil {
					code = 1
					stderr.WriteString(err.Error())
				}
			} else {
				code = execute(t.Context(), args, &stdout, &stderr)
			}
			report, err := os.ReadFile(filepath.Join(dir, "report.txt"))
			if err != nil {
				t.Fatalf("report: %v; stderr: %s", err, &stderr)
			}
			archive := filepath.Join(dir, archiveName)
			if test.failure != "" {
				_, statErr := os.Stat(archive)
				if code != 1 || !strings.Contains(string(report), test.failure) || !os.IsNotExist(statErr) {
					t.Fatalf("exit=%d archive=%v report:\n%s", code, statErr, report)
				}
				return
			}
			if code != 0 || !strings.Contains(string(report), "数据指纹=") {
				t.Fatalf("exit=%d stderr=%s report:\n%s", code, &stderr, report)
			}
			if test.stage == "multi" && (!strings.Contains(string(report), "checker 验证参考解 [cpp] verdict=AC") ||
				!strings.Contains(string(report), "checker 验证参考解 [pypy] verdict=AC")) {
				t.Fatalf("missing language results:\n%s", report)
			}
			assertArchive(t, archive)
		})
	}
}

func assertArchive(t *testing.T, path string) {
	t.Helper()
	z, err := zip.OpenReader(path)
	if err != nil {
		t.Fatal(err)
	}
	defer z.Close()
	var entries []string
	for _, f := range z.File {
		r, err := f.Open()
		if err != nil {
			t.Fatal(err)
		}
		content, err := io.ReadAll(r)
		r.Close()
		if err != nil {
			t.Fatal(err)
		}
		entries = append(entries, f.Name+"="+string(content))
	}
	want := []string{"boundary.in=2 0\n", "boundary.out=2\n", "sample.in=1 1\n", "sample.out=2\n"}
	if !slices.Equal(entries, want) {
		t.Fatalf("archive = %q, want %q", entries, want)
	}
}
