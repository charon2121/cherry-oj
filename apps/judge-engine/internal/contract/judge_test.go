package contract

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// 契约对齐：直接读取 judge.schema.json 的示例，而不是在测试里复制一份“看起来一样”的 JSON。
// schema 和 Go 类型会漂移，用同一份示例才能让字段改名在测试里立即暴露。
func TestJudgeRequestUnmarshalsSchemaExample(t *testing.T) {
	body := judgeSchemaExample(t, "JudgeRequest")

	var req JudgeRequest
	if err := json.Unmarshal(body, &req); err != nil {
		t.Fatal(err)
	}

	if req.SubmissionID == "" || req.ProblemID == "" || req.ProblemVersionID == "" ||
		req.TestDataVersionID == "" || req.LanguageID != "cpp" || req.Source == "" {
		t.Errorf("必填字段没解全: %+v", req)
	}
	if req.Limits.CPUNs != 1000000000 || req.Limits.MemoryBytes != 268435456 {
		t.Errorf("limits 没解对: %+v", req.Limits)
	}
	// mode 缺省是零值 ""；兜底成 submit 由 flow 入口负责，不在 contract 里做。
	if req.Mode != "" {
		t.Errorf("Mode=%q，contract 不该自己兜底", req.Mode)
	}
}

func TestJudgeResultUnmarshalsSchemaExample(t *testing.T) {
	body := judgeSchemaExample(t, "JudgeResult")

	var result JudgeResult
	if err := json.Unmarshal(body, &result); err != nil {
		t.Fatal(err)
	}
	if result.Verdict != VerdictAC || result.EnvironmentFingerprint == "" ||
		result.CPUNs == 0 || result.MemoryBytes == 0 || len(result.CaseResults) != 1 {
		t.Errorf("JudgeResult 示例没有完整落入 Go 类型: %+v", result)
	}
	if result.CaseResults[0].CPUNs == 0 || result.CaseResults[0].MemoryBytes == 0 {
		t.Errorf("CaseResult v2 资源字段没有解码: %+v", result.CaseResults[0])
	}
}

// trial case 的 name 是跨语言契约字段：server 发来后，judge 会原样回填到 CaseResult.Name。
func TestCaseSpecNameRoundTrip(t *testing.T) {
	want := CaseSpec{Input: "1 2\n", Expected: "3\n", Name: "样例 1"}

	body, err := json.Marshal(want)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(body), `"name":"样例 1"`) {
		t.Fatalf("CaseSpec.name 没有编码进 JSON: %s", body)
	}

	var got CaseSpec
	if err := json.Unmarshal(body, &got); err != nil {
		t.Fatal(err)
	}
	if got != want {
		t.Errorf("round trip=%+v want %+v", got, want)
	}
}

// 零值必须被挡住：cpuNs=0 会让每个测试点秒 TLE，memoryBytes=0 会秒 MLE
func TestJudgeLimitsValidate(t *testing.T) {
	tests := []struct {
		name    string
		lim     JudgeLimits
		wantErr bool
	}{
		{"正常", JudgeLimits{CPUNs: 1, MemoryBytes: 1}, false},
		{"带 clockNs 也正常", JudgeLimits{CPUNs: 1, MemoryBytes: 1, ClockNs: 1}, false},
		{"全零值", JudgeLimits{}, true},
		{"cpuNs 为零", JudgeLimits{MemoryBytes: 1}, true},
		{"memoryBytes 为零", JudgeLimits{CPUNs: 1}, true},
		{"cpuNs 为负", JudgeLimits{CPUNs: -1, MemoryBytes: 1}, true},
		{"clockNs 为负", JudgeLimits{CPUNs: 1, MemoryBytes: 1, ClockNs: -1}, true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.lim.Validate()
			if (err != nil) != tt.wantErr {
				t.Errorf("Validate()=%v wantErr=%v", err, tt.wantErr)
			}
		})
	}
}

// limits 缺省时解出来是零值 —— 所以 flow 入口必须调 Validate，
// 否则一次「限制为 0」的判题会全程静默跑完，结论看着还挺合理。
func TestJudgeRequestWithoutLimitsIsInvalid(t *testing.T) {
	const body = `{"submissionId":"s1","problemId":"p1","problemVersionId":"pv1","testDataVersionId":"tdv1","languageId":"cpp","source":"x"}`

	var req JudgeRequest
	if err := json.Unmarshal([]byte(body), &req); err != nil {
		t.Fatal(err)
	}
	if err := req.Limits.Validate(); err == nil {
		t.Error("缺 limits 的请求应当校验失败")
	}
}

func TestJudgeModeIsValid(t *testing.T) {
	for _, m := range []JudgeMode{ModeSubmit, ModeTrial} {
		if !m.IsValid() {
			t.Errorf("%q 应当合法", m)
		}
	}
	// 空串也不合法：兜底成 submit 是 flow 入口的事，不能靠 IsValid 放行
	for _, m := range []JudgeMode{"", "sumbit", "SUBMIT", "official", "trial "} {
		if m.IsValid() {
			t.Errorf("%q 不该被判为合法", m)
		}
	}
}

func TestJudgeModeUsesVersionedTestdata(t *testing.T) {
	tests := []struct {
		mode         JudgeMode
		usesTestdata bool
	}{
		{ModeSubmit, true}, // 测例在磁盘上
		{ModeTrial, false}, // 测例在请求里
	}
	for _, tt := range tests {
		t.Run(string(tt.mode), func(t *testing.T) {
			if got := tt.mode.UsesVersionedTestdata(); got != tt.usesTestdata {
				t.Errorf("UsesVersionedTestdata()=%v want %v", got, tt.usesTestdata)
			}
		})
	}
}

// Output / Diff 是指针字段：不填时必须整个消失，不能出现 "output":null
func TestCaseResultOmitsOutputAndDiff(t *testing.T) {
	b, err := json.Marshal(CaseResult{Idx: 1, Verdict: VerdictAC})
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]any
	if err := json.Unmarshal(b, &m); err != nil {
		t.Fatal(err)
	}
	for _, key := range []string{"output", "diff", "name", "message"} {
		if _, ok := m[key]; ok {
			t.Errorf("AC 的结果不该带 %q，得到 %s", key, b)
		}
	}
}

// revealExpected 关掉时 Diff 不能带 want —— 带了等于把题库答案送给用户。
// 这条断言保护的是「不泄题」这个不变量，而不是某段实现。
func TestDiffOmitsWantWhenNotRevealed(t *testing.T) {
	concealed := CaseResult{
		Idx:     1,
		Verdict: VerdictWA,
		Diff:    &Diff{Line: 17, Got: "42"}, // Want 留空
	}

	b, err := json.Marshal(concealed)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(b), `"want"`) {
		t.Errorf("revealExpected=false 时响应里出现了 want: %s", b)
	}
	if !strings.Contains(string(b), `"line":17`) {
		t.Errorf("行号应当保留（用户靠它定位）: %s", b)
	}

	// 配置打开时（教学场景）才允许带 want
	revealed := CaseResult{Idx: 1, Verdict: VerdictWA, Diff: &Diff{Line: 17, Got: "42", Want: "43"}}
	b2, err := json.Marshal(revealed)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(b2), `"want":"43"`) {
		t.Errorf("revealExpected=true 时应当能带 want: %s", b2)
	}
}

func TestOutputEncodes(t *testing.T) {
	b, err := json.Marshal(Output{Excerpt: "42\n", Bytes: 3})
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]any
	if err := json.Unmarshal(b, &m); err != nil {
		t.Fatal(err)
	}
	// excerpt/bytes 是必填，即使为零值也要出现
	for _, key := range []string{"excerpt", "bytes"} {
		if _, ok := m[key]; !ok {
			t.Errorf("%q 应当总是出现: %s", key, b)
		}
	}
	// truncated/ref 没值时应当消失
	for _, key := range []string{"truncated", "ref"} {
		if _, ok := m[key]; ok {
			t.Errorf("%q 为空时应被 omitempty 掉: %s", key, b)
		}
	}
}

func TestJudgeResultOmitsEmpty(t *testing.T) {
	b, err := json.Marshal(JudgeResult{
		Verdict:                VerdictAC,
		EnvironmentFingerprint: "sha256:test-environment",
		Score:                  100,
	})
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]any
	if err := json.Unmarshal(b, &m); err != nil {
		t.Fatal(err)
	}

	for _, key := range []string{"verdict", "environmentFingerprint", "score"} {
		if _, ok := m[key]; !ok {
			t.Errorf("%q 应当总是出现", key)
		}
	}
	for _, key := range []string{"caseResults", "message", "cpuNs", "memoryBytes"} {
		if _, ok := m[key]; ok {
			t.Errorf("%q 为空时应被 omitempty 掉", key)
		}
	}
}

func TestJudgeResultUsesV2ResourceNames(t *testing.T) {
	b, err := json.Marshal(JudgeResult{
		Verdict:                VerdictAC,
		EnvironmentFingerprint: "sha256:test-environment",
		CPUNs:                  12,
		MemoryBytes:            34,
		CaseResults: []CaseResult{{
			Idx:         1,
			Verdict:     VerdictAC,
			CPUNs:       10,
			MemoryBytes: 30,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}

	encoded := string(b)
	for _, key := range []string{`"cpuNs"`, `"memoryBytes"`, `"caseResults"`} {
		if !strings.Contains(encoded, key) {
			t.Errorf("v2 字段 %s 没有编码: %s", key, encoded)
		}
	}
	for _, oldKey := range []string{`"time"`, `"memory"`, `"cases"`} {
		if strings.Contains(encoded, oldKey) {
			t.Errorf("结果仍包含旧字段 %s: %s", oldKey, encoded)
		}
	}
}

func judgeSchemaExample(t *testing.T, definition string) json.RawMessage {
	t.Helper()

	path := filepath.Join("..", "..", "..", "..", "contracts", "judge.schema.json")
	body, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("读取 %s: %v", path, err)
	}
	var schema struct {
		Definitions map[string]struct {
			Examples []json.RawMessage `json:"examples"`
		} `json:"definitions"`
	}
	if err := json.Unmarshal(body, &schema); err != nil {
		t.Fatalf("解析 %s: %v", path, err)
	}
	examples := schema.Definitions[definition].Examples
	if len(examples) == 0 {
		t.Fatalf("%s definitions.%s.examples 不能为空", path, definition)
	}
	return examples[0]
}
