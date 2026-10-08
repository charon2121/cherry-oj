package api_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/api"
)

type fakeJudger struct {
	called int
	got    contract.JudgeRequest
	result contract.JudgeResult
	ctx    context.Context
}

func (f *fakeJudger) Judge(ctx context.Context, req contract.JudgeRequest) contract.JudgeResult {
	f.called++
	f.got = req
	f.ctx = ctx
	return f.result
}

func TestVersion(t *testing.T) {
	h := api.New(&fakeJudger{}).Handler()
	req := httptest.NewRequest(http.MethodGet, "/version", nil)
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("code = %d, body = %s", rec.Code, rec.Body)
	}
	if got := rec.Header().Get("Content-Type"); got != "application/json" {
		t.Errorf("Content-Type = %q", got)
	}
	var body map[string]string
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	if body["name"] != "cherry-oj-judge" || body["version"] == "" {
		t.Errorf("body = %v", body)
	}
}

func TestJudgeDecodesAndForwardsRequest(t *testing.T) {
	fake := &fakeJudger{result: contract.JudgeResult{
		Verdict: contract.VerdictAC,
		Score:   100,
	}}
	h := api.New(fake).Handler()
	body := `{
      "submissionId":"s1",
      "problemId":"a-plus-b",
      "languageId":"cpp",
      "source":"",
      "limits":{"cpuNs":1000,"memoryBytes":2000,"clockNs":3000},
      "mode":"trial",
      "testcases":[{"input":"1 2\n","expected":"3\n","name":"sample"}]
    }`
	type contextKey string
	const key contextKey = "request-id"
	req := httptest.NewRequest(http.MethodPost, "/judge", strings.NewReader(body))
	req = req.WithContext(context.WithValue(req.Context(), key, "trace-1"))
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("code = %d, body = %s", rec.Code, rec.Body)
	}
	if fake.called != 1 {
		t.Fatalf("Judge calls = %d", fake.called)
	}
	if fake.got.SubmissionID != "s1" || fake.got.ProblemID != "a-plus-b" || fake.got.TestDataLocation != "" ||
		fake.got.LanguageID != "cpp" || fake.got.Source != "" || fake.got.Mode != contract.ModeTrial {
		t.Errorf("request = %+v", fake.got)
	}
	if fake.got.Limits.CPUNs != 1000 || fake.got.Limits.MemoryBytes != 2000 || fake.got.Limits.ClockNs != 3000 {
		t.Errorf("limits = %+v", fake.got.Limits)
	}
	if len(fake.got.Testcases) != 1 || fake.got.Testcases[0].Input != "1 2\n" ||
		fake.got.Testcases[0].Expected != "3\n" || fake.got.Testcases[0].Name != "sample" {
		t.Errorf("testcases = %+v", fake.got.Testcases)
	}
	if got := fake.ctx.Value(key); got != "trace-1" {
		t.Errorf("request context was not forwarded: %v", got)
	}

	var result contract.JudgeResult
	if err := json.Unmarshal(rec.Body.Bytes(), &result); err != nil {
		t.Fatal(err)
	}
	if result.Verdict != contract.VerdictAC || result.Score != 100 {
		t.Errorf("result = %+v", result)
	}
}

func TestJudgeForwardsTestDataLocationInSubmitMode(t *testing.T) {
	fake := &fakeJudger{result: contract.JudgeResult{Verdict: contract.VerdictAC}}
	h := api.New(fake).Handler()
	body := `{"submissionId":"s","problemId":"p","testDataLocation":"https://data.example/p/","languageId":"cpp","source":"x","limits":{"cpuNs":1,"memoryBytes":1}}`
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/judge", strings.NewReader(body)))

	if rec.Code != http.StatusOK {
		t.Fatalf("code = %d, body = %s", rec.Code, rec.Body)
	}
	if fake.got.TestDataLocation != "https://data.example/p/" {
		t.Errorf("TestDataLocation = %q", fake.got.TestDataLocation)
	}
	if fake.got.Mode != "" {
		t.Errorf("Mode = %q，缺省的 mode 由 flow 兜底，api 不替它决定", fake.got.Mode)
	}
}

func TestJudgeRejectsMalformedOrIncompleteJSON(t *testing.T) {
	tests := []struct {
		name string
		body string
		want string
	}{
		{"empty body", "", "parse JudgeRequest"},
		{"invalid JSON", "{", "parse JudgeRequest"},
		{"multiple values", `{}` + `{}`, "extra JSON value"},
		{"unknown field", `{"submissionId":"s","problemId":"p","testDataLocation":"/data/p","languageId":"cpp","source":"x","limits":{"cpuNs":1,"memoryBytes":1},"codeMode":"CORE"}`, "unknown field"},
		{"missing submissionId", `{"problemId":"p","testDataLocation":"/data/p","languageId":"cpp","source":"x","limits":{"cpuNs":1,"memoryBytes":1}}`, "submissionId"},
		{"missing problemId", `{"submissionId":"s","testDataLocation":"/data/p","languageId":"cpp","source":"x","limits":{"cpuNs":1,"memoryBytes":1}}`, "problemId"},
		{"missing testDataLocation", `{"submissionId":"s","problemId":"p","languageId":"cpp","source":"x","limits":{"cpuNs":1,"memoryBytes":1}}`, "testDataLocation"},
		{"missing testDataLocation in explicit submit mode", `{"submissionId":"s","problemId":"p","languageId":"cpp","source":"x","limits":{"cpuNs":1,"memoryBytes":1},"mode":"submit"}`, "testDataLocation"},
		// 版本字段已经删除，不做兼容：旧调用方带着它们发请求，必须被明确拒绝而不是悄悄忽略。
		{"removed problemVersionId", `{"submissionId":"s","problemId":"p","problemVersionId":"pv","testDataLocation":"/data/p","languageId":"cpp","source":"x","limits":{"cpuNs":1,"memoryBytes":1}}`, "unknown field"},
		{"removed testDataVersionId", `{"submissionId":"s","problemId":"p","testDataVersionId":"tdv","languageId":"cpp","source":"x","limits":{"cpuNs":1,"memoryBytes":1}}`, "unknown field"},
		{"missing languageId", `{"submissionId":"s","problemId":"p","testDataLocation":"/data/p","source":"x","limits":{"cpuNs":1,"memoryBytes":1}}`, "languageId"},
		{"missing source", `{"submissionId":"s","problemId":"p","testDataLocation":"/data/p","languageId":"cpp","limits":{"cpuNs":1,"memoryBytes":1}}`, "source"},
		{"missing limits", `{"submissionId":"s","problemId":"p","testDataLocation":"/data/p","languageId":"cpp","source":"x"}`, "limits"},
		{"missing cpuNs", `{"submissionId":"s","problemId":"p","testDataLocation":"/data/p","languageId":"cpp","source":"x","limits":{"memoryBytes":1}}`, "limits.cpuNs"},
		{"missing memoryBytes", `{"submissionId":"s","problemId":"p","testDataLocation":"/data/p","languageId":"cpp","source":"x","limits":{"cpuNs":1}}`, "limits.memoryBytes"},
		{"missing case input", `{"submissionId":"s","problemId":"p","testDataLocation":"/data/p","languageId":"cpp","source":"x","limits":{"cpuNs":1,"memoryBytes":1},"mode":"trial","testcases":[{"expected":"3"}]}`, "testcases[0].input"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fake := &fakeJudger{}
			h := api.New(fake).Handler()
			req := httptest.NewRequest(http.MethodPost, "/judge", strings.NewReader(tt.body))
			rec := httptest.NewRecorder()

			h.ServeHTTP(rec, req)

			if rec.Code != http.StatusBadRequest {
				t.Fatalf("code = %d, body = %s", rec.Code, rec.Body)
			}
			if fake.called != 0 {
				t.Errorf("invalid request reached Judger")
			}
			if !strings.Contains(rec.Body.String(), tt.want) {
				t.Errorf("body = %s, want substring %q", rec.Body, tt.want)
			}
		})
	}
}

func TestJudgeVerdictsAreHTTP200(t *testing.T) {
	for _, verdict := range []contract.Verdict{
		contract.VerdictWA,
		contract.VerdictTLE,
		contract.VerdictCE,
		contract.VerdictRE,
		contract.VerdictSE,
	} {
		t.Run(string(verdict), func(t *testing.T) {
			fake := &fakeJudger{result: contract.JudgeResult{Verdict: verdict}}
			h := api.New(fake).Handler()
			req := httptest.NewRequest(http.MethodPost, "/judge", strings.NewReader(
				`{"submissionId":"s","problemId":"p","testDataLocation":"/data/p","languageId":"cpp","source":"x","limits":{"cpuNs":1,"memoryBytes":1}}`,
			))
			rec := httptest.NewRecorder()

			h.ServeHTTP(rec, req)

			if rec.Code != http.StatusOK {
				t.Fatalf("verdict %s returned HTTP %d: %s", verdict, rec.Code, rec.Body)
			}
		})
	}
}

func TestJudgeWrongMethodIsRejected(t *testing.T) {
	h := api.New(&fakeJudger{}).Handler()
	req := httptest.NewRequest(http.MethodGet, "/judge", nil)
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("code = %d, want 405", rec.Code)
	}
}

// 请求体超过上限时拒绝为 413，且不调用判题：否则一个超大请求会被整份读进内存。
func TestJudgeRejectsOversizedBody(t *testing.T) {
	fake := &fakeJudger{}
	h := api.New(fake).Handler()
	source := strings.Repeat("x", 17<<20)
	body := `{"submissionId":"s","problemId":"p","testDataLocation":"/data/p","languageId":"cpp","source":"` + source + `","limits":{"cpuNs":1,"memoryBytes":1}}`
	req := httptest.NewRequest(http.MethodPost, "/judge", strings.NewReader(body))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusRequestEntityTooLarge || fake.called != 0 {
		t.Fatalf("status=%d called=%d", rec.Code, fake.called)
	}
}
