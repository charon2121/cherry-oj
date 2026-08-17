package client_test

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/judge/client"
)

func TestUpload(t *testing.T) {
	const payload = "source code"
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/blobs" {
			t.Errorf("request = %s %s, want POST /blobs", r.Method, r.URL.Path)
		}
		if got := r.Header.Get("Content-Type"); got != "application/octet-stream" {
			t.Errorf("Content-Type = %q", got)
		}
		body, err := io.ReadAll(r.Body)
		if err != nil {
			t.Errorf("read request body: %v", err)
			return
		}
		if string(body) != payload {
			t.Errorf("body = %q, want %q", body, payload)
		}
		_ = json.NewEncoder(w).Encode(map[string]string{"ref": "0123456789abcdef0123456789abcdef"})
	}))
	defer srv.Close()

	c := client.New(srv.URL+"/", time.Second) // 末尾斜杠不能变成 //blobs
	ref, err := c.Upload(context.Background(), strings.NewReader(payload))
	if err != nil {
		t.Fatalf("Upload: %v", err)
	}
	if ref != "0123456789abcdef0123456789abcdef" {
		t.Errorf("ref = %q", ref)
	}
}

func TestUploadRejectsNilBody(t *testing.T) {
	c := client.New("http://127.0.0.1", time.Second)
	if _, err := c.Upload(context.Background(), nil); err == nil {
		t.Fatal("nil body should fail before making a request")
	}
}

func TestUploadRequiresRef(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = io.WriteString(w, `{}`)
	}))
	defer srv.Close()

	c := client.New(srv.URL, time.Second)
	if _, err := c.Upload(context.Background(), strings.NewReader("x")); err == nil {
		t.Fatal("response without ref should fail")
	}
}

func TestRunEncodesSpec(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/run" {
			t.Errorf("request = %s %s, want POST /run", r.Method, r.URL.Path)
		}
		if got := r.Header.Get("Content-Type"); got != "application/json" {
			t.Errorf("Content-Type = %q", got)
		}

		var got contract.RunSpec
		if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
			t.Errorf("decode request: %v", err)
			return
		}
		if strings.Join(got.Command, " ") != "g++ main.cpp" {
			t.Errorf("command = %v", got.Command)
		}
		_ = json.NewEncoder(w).Encode(contract.RunResult{
			Status: contract.StatusOK,
			Stdout: "3\n",
		})
	}))
	defer srv.Close()

	c := client.New(srv.URL, time.Second)
	result, err := c.Run(context.Background(), contract.RunSpec{Command: []string{"g++", "main.cpp"}})
	if err != nil {
		t.Fatalf("Run: %v", err)
	}
	if result.Status != contract.StatusOK || result.Stdout != "3\n" {
		t.Errorf("result = %+v", result)
	}
}

// TLE 是被运行程序的事实，不是 judge 和 sandbox 的 HTTP 对话失败。
func TestRunNonOKStatusIsNotError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(contract.RunResult{Status: contract.StatusTimeLimitExceeded})
	}))
	defer srv.Close()

	c := client.New(srv.URL, time.Second)
	result, err := c.Run(context.Background(), contract.RunSpec{Command: []string{"sleep", "10"}})
	if err != nil {
		t.Fatalf("TLE should not be an HTTP error: %v", err)
	}
	if result.Status != contract.StatusTimeLimitExceeded {
		t.Errorf("status = %s", result.Status)
	}
}

func TestRunHTTPErrorIncludesBody(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, `{"error":"sandbox busy"}`, http.StatusServiceUnavailable)
	}))
	defer srv.Close()

	c := client.New(srv.URL, time.Second)
	_, err := c.Run(context.Background(), contract.RunSpec{Command: []string{"true"}})
	if err == nil {
		t.Fatal("503 should fail")
	}
	for _, want := range []string{"503", "sandbox busy"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("error %q does not contain %q", err, want)
		}
	}
}

func TestRunRejectsMalformedResponse(t *testing.T) {
	tests := []struct {
		name string
		body string
	}{
		{"invalid JSON", "{"},
		{"missing status", `{}`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				_, _ = io.WriteString(w, tt.body)
			}))
			defer srv.Close()

			c := client.New(srv.URL, time.Second)
			if _, err := c.Run(context.Background(), contract.RunSpec{Command: []string{"true"}}); err == nil {
				t.Fatalf("response %q should fail", tt.body)
			}
		})
	}
}

func TestDeleteIsIdempotent(t *testing.T) {
	for _, status := range []int{http.StatusOK, http.StatusNoContent, http.StatusNotFound} {
		t.Run(http.StatusText(status), func(t *testing.T) {
			const ref = "0123456789abcdef0123456789abcdef"
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != http.MethodDelete || r.URL.Path != "/blobs/"+ref {
					t.Errorf("request = %s %s", r.Method, r.URL.Path)
				}
				w.WriteHeader(status)
			}))
			defer srv.Close()

			c := client.New(srv.URL, time.Second)
			if err := c.Delete(context.Background(), ref); err != nil {
				t.Fatalf("Delete status %d: %v", status, err)
			}
		})
	}
}

func TestDeleteHTTPErrorIncludesBody(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "store unavailable", http.StatusInternalServerError)
	}))
	defer srv.Close()

	c := client.New(srv.URL, time.Second)
	err := c.Delete(context.Background(), "0123456789abcdef0123456789abcdef")
	if err == nil {
		t.Fatal("500 should fail")
	}
	if !strings.Contains(err.Error(), "store unavailable") {
		t.Errorf("error = %q", err)
	}
}

func TestRunPropagatesContextCancellation(t *testing.T) {
	c := client.New("http://127.0.0.1", time.Second)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := c.Run(ctx, contract.RunSpec{Command: []string{"true"}})
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("error = %v, want context.Canceled", err)
	}
}

func TestClientTimeout(t *testing.T) {
	release := make(chan struct{})
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		<-release
	}))
	t.Cleanup(func() {
		close(release)
		srv.Close()
	})

	c := client.New(srv.URL, 20*time.Millisecond)
	started := time.Now()
	_, err := c.Run(context.Background(), contract.RunSpec{Command: []string{"true"}})
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("error = %v, want context deadline exceeded", err)
	}
	if elapsed := time.Since(started); elapsed > time.Second {
		t.Fatalf("timeout took %s", elapsed)
	}
}
