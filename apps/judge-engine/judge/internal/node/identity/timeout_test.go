package identity_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	platform "cherry-oj/judge-engine/internal/platform/config"
	"cherry-oj/judge-engine/judge/internal/config"
	"cherry-oj/judge-engine/judge/internal/flow"
	"cherry-oj/judge-engine/judge/internal/node/identity"
	client "cherry-oj/judge-engine/judge/internal/sandboxclient"
)

func TestTimeoutChangesVerdictAndFingerprint(t *testing.T) {
	sb := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodDelete {
			w.WriteHeader(204)
			return
		}
		if r.URL.Path == "/blobs" {
			w.Write([]byte(`{"ref":"src"}`))
			return
		}
		var spec contract.RunSpec
		if e := json.NewDecoder(r.Body).Decode(&spec); e != nil {
			t.Error(e)
			return
		}
		if spec.Command[0] == "g++" {
			_ = json.NewEncoder(w).Encode(contract.RunResult{Status: contract.StatusOK, Artifacts: map[string]string{"Main": "exe"}})
			return
		}
		select {
		case <-time.After(time.Duration(spec.Limits.ClockNs)):
			_ = json.NewEncoder(w).Encode(contract.RunResult{Status: contract.StatusTimeLimitExceeded})
		case <-r.Context().Done():
		}
	}))
	defer sb.Close()
	fingerprints := []string{}
	verdicts := []contract.Verdict{}
	for _, timeout := range []time.Duration{25 * time.Millisecond, time.Second} {
		cfg := config.Default()
		cfg.Judge.SandboxURL = sb.URL
		cfg.Judge.Compile.ClockNs = int64(20 * time.Millisecond)
		cfg.Judge.Compile.CPUNs = int64(10 * time.Millisecond)
		cfg.Judge.SandboxTimeout = platform.Duration(timeout)
		if e := cfg.Validate(); e != nil {
			t.Fatal(e)
		}
		id, e := identity.New(cfg.Judge, identity.Declared(cfg.Judge))
		if e != nil {
			t.Fatal(e)
		}
		fp := id.Registration().EnvironmentFingerprint
		req := contract.JudgeRequest{Mode: contract.ModeTrial, LanguageID: "cpp", Source: "int main(){}", Cases: []contract.CaseSpec{{Name: "slow", Input: ""}}, Limits: contract.JudgeLimits{CPUNs: 1_000_000, MemoryBytes: 1 << 20, ClockNs: int64(50 * time.Millisecond)}}
		result := flow.Judge(context.Background(), client.New(sb.URL, timeout), cfg.Judge, req)
		fingerprints = append(fingerprints, fp)
		verdicts = append(verdicts, result.Verdict)
		t.Logf("timeout=%s validation=pass fingerprint=%s verdict=%s", timeout, fp, result.Verdict)
	}
	if verdicts[0] != contract.VerdictSE || verdicts[1] != contract.VerdictTLE {
		t.Fatalf("verdicts=%v, want [SE TLE]", verdicts)
	}
	if fingerprints[0] == fingerprints[1] {
		t.Error("same fingerprint permits different execution verdicts")
	}
}
