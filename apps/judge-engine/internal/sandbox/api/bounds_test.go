package api_test

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/api"
)

type countExecutor struct{ calls int }

func (e *countExecutor) Run(context.Context, contract.RunSpec) (contract.RunResult, error) {
	e.calls++
	return contract.RunResult{Status: contract.StatusOK}, nil
}
func TestRunBodyRejectsBeforeExecution(t *testing.T) {
	for _, body := range []string{`{"command":["true"]} {}`, `{"command":["true"],"unknown":1}`, `{"command":["true"],"limits":{"cpuNs":null}}`, strings.Repeat(" ", 129)} {
		e := &countExecutor{}
		handler := api.New(e, nil, api.Options{MaxRequestBytes: 128}).Handler()
		w := httptest.NewRecorder()
		handler.ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/run", strings.NewReader(body)))
		if w.Code != http.StatusBadRequest || e.calls != 0 {
			t.Fatalf("status=%d calls=%d body=%q", w.Code, e.calls, body)
		}
	}
}
func TestVersionReflectsBackend(t *testing.T) {
	w := httptest.NewRecorder()
	api.New(nil, nil, api.Options{Isolation: "linux"}).Handler().ServeHTTP(w, httptest.NewRequest("GET", "/version", nil))
	b, e := io.ReadAll(w.Result().Body)
	if e != nil || !strings.Contains(string(b), `"isolation":"linux"`) {
		t.Fatalf("body=%q err=%v", b, e)
	}
}
