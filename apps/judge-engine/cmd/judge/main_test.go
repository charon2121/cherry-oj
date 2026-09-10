package main

import (
	"encoding/json"
	"flag"
	"log/slog"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync/atomic"
	"testing"
)

func TestOccupiedListenerDoesNotRegisterNode(t *testing.T) {
	occupied, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer occupied.Close()
	var registrations atomic.Int32
	control := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { registrations.Add(1); w.WriteHeader(503) }))
	defer control.Close()
	sandbox := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/version" {
			_, err := w.Write([]byte(`{"name":"cherry-oj-sandbox","version":"test","isolation":"host"}`))
			if err != nil {
				t.Error(err)
			}
			return
		}
		if r.URL.Path != "/run" {
			t.Errorf("unexpected route: %s", r.URL.Path)
			w.WriteHeader(404)
			return
		}
		metadata := `{"Architecture":"amd64","CPUModel":"test","OSVersion":"test","KernelVersion":"test","ToolchainVersion":"test","RuntimeDigest":"test"}`
		if err := json.NewEncoder(w).Encode(map[string]any{"status": "OK", "exitCode": 0, "stdout": metadata}); err != nil {
			t.Error(err)
		}
	}))
	defer sandbox.Close()
	root := t.TempDir()
	path := filepath.Join(root, "config.json")
	config := map[string]any{"logging": map[string]any{"path": root}, "judge": map[string]any{"httpAddr": occupied.Addr().String(), "sandboxURL": sandbox.URL, "testdataRoot": filepath.Join(root, "testdata"), "node": map[string]any{"enabled": true, "controlToken": "test-control", "controlPlaneURL": control.URL}}}
	data, err := json.Marshal(config)
	if err != nil {
		t.Fatal(err)
	}
	if err = os.WriteFile(path, data, 0600); err != nil {
		t.Fatal(err)
	}
	oldArgs, oldFlags, oldLogger := os.Args, flag.CommandLine, slog.Default()
	defer func() { os.Args = oldArgs; flag.CommandLine = oldFlags; slog.SetDefault(oldLogger) }()
	os.Args = []string{"judge", "-config", path}
	flag.CommandLine = flag.NewFlagSet("judge-test", flag.ContinueOnError)
	if code := run(); code != 1 {
		t.Fatalf("occupied listener returned %d", code)
	}
	if registrations.Load() != 0 {
		t.Fatal("advertised an endpoint that could not bind")
	}
}
