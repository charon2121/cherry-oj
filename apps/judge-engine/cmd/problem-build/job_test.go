package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"os"
	"path/filepath"
	"testing"
)

func TestJobRejectsUnknownFieldAndWritesFailureArtifacts(t *testing.T) {
	dir := t.TempDir()
	input := filepath.Join(dir, "input.json")
	if err := os.WriteFile(input, []byte(`{"schemaVersion":1,"unknown":true}`), 0o600); err != nil {
		t.Fatal(err)
	}
	output := filepath.Join(dir, "attempt")
	var diagnostics bytes.Buffer
	if code := execute(t.Context(), []string{
		"-job", input, "-output", output, "-judge-url", "http://127.0.0.1:15051", "-toolchain-root", dir,
	}, io.Discard, &diagnostics); code != 1 {
		t.Fatalf("exit=%d diagnostics=%s", code, &diagnostics)
	}
	var result runnerResult
	readJobFile(t, output, "result.json", &result)
	if result.SchemaVersion != 1 || result.Stage != "INPUT" || result.Status != "FAILED" ||
		result.TestDataDigest != "" || result.Languages == nil {
		t.Fatalf("result = %+v", result)
	}
	var progress runnerProgress
	readJobFile(t, output, "progress.json", &progress)
	if progress != result.runnerProgress {
		t.Fatalf("progress = %+v, result = %+v", progress, result)
	}
	if _, err := os.Stat(filepath.Join(output, "report.txt")); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(output, archiveName)); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("failure created a ZIP: %v", err)
	}
}

func readJobFile(t *testing.T, output, name string, dest any) {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(output, name))
	if err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal(data, dest); err != nil {
		t.Fatal(err)
	}
}
