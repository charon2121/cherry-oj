package api_test

import (
	"bytes"
	"encoding/json"
	"net/http"
	"os"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
)

// 显式指向独立 Linux judge 容器才执行。不要指向承载真实提交的节点：
// 大输出用于复现 host sandbox 的跨请求内存统计污染，而非日常 smoke。
func TestTrialLinuxOutputIsolation(t *testing.T) {
	endpoint := os.Getenv("CHERRY_TRIAL_E2E_URL")
	if endpoint == "" {
		t.Skip("requires an isolated Linux judge/sandbox pair")
	}
	client := &http.Client{Timeout: 20 * time.Second}
	run := func(source string) contract.JudgeResult {
		t.Helper()
		request := contract.JudgeRequest{SubmissionID: "work044-isolation-test", ProblemID: "problem-a-plus-b", ProblemVersionID: "problem-a-plus-b-v1", TestDataVersionID: "a-plus-b", LanguageID: "cpp", Source: source, Mode: contract.ModeTrial, Cases: []contract.CaseSpec{{Input: ""}}, Limits: contract.JudgeLimits{CPUNs: 2_000_000_000, ClockNs: 4_000_000_000, MemoryBytes: 268435456}}
		body, err := json.Marshal(request)
		if err != nil {
			t.Fatal(err)
		}
		res, err := client.Post(endpoint+"/judge", "application/json", bytes.NewReader(body))
		if err != nil {
			t.Fatal(err)
		}
		defer res.Body.Close()
		if res.StatusCode != 200 {
			t.Fatalf("HTTP %d", res.StatusCode)
		}
		var result contract.JudgeResult
		if err := json.NewDecoder(res.Body).Decode(&result); err != nil {
			t.Fatal(err)
		}
		return result
	}
	before := run("int main(){}")
	if before.Verdict != contract.VerdictRAN {
		t.Fatalf("clean node: %s, memoryBytes=%d", before.Verdict, before.MemoryBytes)
	}
	pressure := run("#include <cstdio>\n#include <cstring>\nint main(){char b[65536];memset(b,120,sizeof b);for(int i=0;i<1500;i++) fwrite(b,1,sizeof b,stdout);}")
	if pressure.Verdict != contract.VerdictOLE {
		t.Fatalf("bounded output pressure: %s", pressure.Verdict)
	}
	after := run("int main(){}")
	if after.Verdict != contract.VerdictRAN {
		t.Fatalf("after output pressure: %s, memoryBytes=%d; previous output must not affect later programs", after.Verdict, after.MemoryBytes)
	}
}
