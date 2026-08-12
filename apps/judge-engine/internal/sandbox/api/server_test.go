package api_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/api"
	"cherry-oj/judge-engine/internal/sandbox/store"
)

type fakeExec struct {
	got contract.RunSpec
	res contract.RunResult
	err error
}

func (f *fakeExec) Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error) {
	f.got = spec // 记下来，好断言 api 有没有把 JSON 解对
	return f.res, f.err
}

func TestHandleRunDecodesSpec(t *testing.T) {
	fake := &fakeExec{res: contract.RunResult{Status: contract.StatusOK, Stdout: "hello\n"}}
	h := api.New(fake, nil, api.Options{}).Handler() // store 用不到，传 nil 即可

	body := `{"command":["/bin/echo","hello"],"stdin":"1 2\n"}`
	req := httptest.NewRequest("POST", "/run", strings.NewReader(body))
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("code=%d body=%s", rec.Code, rec.Body)
	}
	// ① api 有没有把裸字符串 stdin 解成 FileSource（步骤 0 的成果）
	if fake.got.Stdin == nil || fake.got.Stdin.Text != "1 2\n" {
		t.Errorf("stdin 没解对: %+v", fake.got.Stdin)
	}
	// ② 响应是不是直接就是 RunResult
	var res contract.RunResult
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatal(err)
	}
	if res.Stdout != "hello\n" {
		t.Errorf("stdout=%q", res.Stdout)
	}
}

func TestHandleRunBadJSON(t *testing.T) {
	h := api.New(&fakeExec{}, nil, api.Options{}).Handler()
	req := httptest.NewRequest("POST", "/run", strings.NewReader("{"))
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("code=%d body=%s, want 400", rec.Code, rec.Body)
	}
}

func TestHandleRunEmptyCommand(t *testing.T) {
	h := api.New(&fakeExec{}, nil, api.Options{}).Handler()
	req := httptest.NewRequest("POST", "/run", strings.NewReader(`{"command":[]}`))
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("code=%d body=%s, want 400", rec.Code, rec.Body)
	}
}

func TestBlobRoundTrip(t *testing.T) {
	st, err := store.NewDiskStoreWithRoot(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	h := api.New(&fakeExec{}, st, api.Options{}).Handler()

	const body = "blob-payload"
	req := httptest.NewRequest("POST", "/blobs", strings.NewReader(body))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("PUT code=%d body=%s", rec.Code, rec.Body)
	}

	var putRes map[string]string
	if err := json.Unmarshal(rec.Body.Bytes(), &putRes); err != nil {
		t.Fatal(err)
	}
	ref := putRes["ref"]
	if ref == "" {
		t.Fatalf("empty ref in %s", rec.Body)
	}

	req = httptest.NewRequest("GET", "/blobs/"+ref, nil)
	rec = httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("GET code=%d body=%s", rec.Code, rec.Body)
	}
	if got := rec.Body.String(); got != body {
		t.Fatalf("GET body=%q want %q", got, body)
	}

	req = httptest.NewRequest("DELETE", "/blobs/"+ref, nil)
	rec = httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("DELETE code=%d body=%s", rec.Code, rec.Body)
	}

	req = httptest.NewRequest("GET", "/blobs/"+ref, nil)
	rec = httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("GET after DELETE code=%d body=%s, want 404", rec.Code, rec.Body)
	}
}

func TestBlobNotFound(t *testing.T) {
	st, err := store.NewDiskStoreWithRoot(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	h := api.New(&fakeExec{}, st, api.Options{}).Handler()

	req := httptest.NewRequest("GET", "/blobs/0123456789abcdef0123456789abcdef", nil)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("code=%d body=%s, want 404", rec.Code, rec.Body)
	}
}

// 上传上限来自 Options，不是写死的常量
func TestBlobPutRespectsMaxBytes(t *testing.T) {
	st, err := store.NewDiskStoreWithRoot(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	h := api.New(&fakeExec{}, st, api.Options{MaxBlobBytes: 8}).Handler()

	req := httptest.NewRequest("POST", "/blobs", strings.NewReader("0123456789")) // 10 > 8
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code == http.StatusOK {
		t.Fatalf("超过 MaxBlobBytes 的上传不该成功: body=%s", rec.Body)
	}
}

// 零值兜底：没配 MaxBlobBytes 不等于「不许上传」
func TestBlobPutZeroOptionUsesDefault(t *testing.T) {
	st, err := store.NewDiskStoreWithRoot(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	h := api.New(&fakeExec{}, st, api.Options{}).Handler()

	req := httptest.NewRequest("POST", "/blobs", strings.NewReader("hi"))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("MaxBlobBytes 零值应当回退到默认上限，code=%d body=%s", rec.Code, rec.Body)
	}
}
