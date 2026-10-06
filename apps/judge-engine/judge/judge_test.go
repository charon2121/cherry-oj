package judge

import (
	"context"
	"encoding/json"
	"io"
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
	root := t.TempDir()
	path := filepath.Join(root, "config.json")
	settings := map[string]any{"logging": map[string]any{"path": root}, "judge": map[string]any{"httpAddr": occupied.Addr().String(), "testdata": map[string]any{"workRoot": filepath.Join(root, "testdata")}, "node": map[string]any{"enabled": true, "controlToken": "test-control", "controlPlaneURL": control.URL}},
		"execution": map[string]any{"backend": "devhost", "allowUnsafeBackend": true, "store": map[string]any{"root": filepath.Join(root, "blobs")}}}
	data, err := json.Marshal(settings)
	if err != nil {
		t.Fatal(err)
	}
	if err = os.WriteFile(path, data, 0600); err != nil {
		t.Fatal(err)
	}
	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatal(err)
	}
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	if err := Run(context.Background(), cfg, logger); err == nil {
		t.Fatal("occupied listener started the service")
	}
	if registrations.Load() != 0 {
		t.Fatal("advertised an endpoint that could not bind")
	}
}
