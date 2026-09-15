package logging

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestJSONLoggerUsesUnifiedFields(t *testing.T) {
	var output bytes.Buffer
	logger := newJSONLogger("judge", &output, 0)
	logger.Info("process.started", "trace_id", "0123456789abcdef0123456789abcdef")

	var event map[string]any
	if err := json.Unmarshal(output.Bytes(), &event); err != nil {
		t.Fatalf("日志不是合法 JSON: %v", err)
	}
	for _, key := range []string{"@timestamp", "level", "message", "service", "trace_id"} {
		if _, ok := event[key]; !ok {
			t.Errorf("缺少统一字段 %q: %s", key, output.String())
		}
	}
	if event["service"] != "judge" || event["message"] != "process.started" {
		t.Errorf("event=%v", event)
	}
}

func TestDailyWriterRotatesOnUTCDate(t *testing.T) {
	directory := t.TempDir()
	now := time.Date(2026, 8, 26, 23, 59, 0, 0, time.UTC)
	w, err := newDailyWriter(directory, "sandbox", func() time.Time { return now })
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = w.Close() })

	if _, err := w.Write([]byte("first\n")); err != nil {
		t.Fatal(err)
	}
	now = now.Add(2 * time.Minute)
	if _, err := w.Write([]byte("second\n")); err != nil {
		t.Fatal(err)
	}

	assertFileContent(t, filepath.Join(directory, "sandbox.2026-08-26.log"), "first\n")
	assertFileContent(t, filepath.Join(directory, "sandbox.2026-08-27.log"), "second\n")
}

func assertFileContent(t *testing.T, path, want string) {
	t.Helper()
	got, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != want {
		t.Errorf("%s=%q want %q", path, got, want)
	}
}
