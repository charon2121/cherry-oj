package main

import (
	"runtime"
	"testing"

	"cherry-oj/judge-engine/internal/config"
)

func TestBackendSelectionNeverFallsBack(t *testing.T) {
	c := config.Default().Sandbox
	c.Backend = "unknown"
	if _, _, e := backend(c); e == nil {
		t.Fatal("unknown accepted")
	}
	if runtime.GOOS != "linux" || runtime.GOARCH != "amd64" {
		c.Backend = "linux"
		if _, _, e := backend(c); e == nil {
			t.Fatal("linux silently fell back")
		}
	}
	c.Backend = "trusted-host"
	factory, close, e := backend(c)
	if e != nil || factory == nil {
		t.Fatalf("trusted development=%v", e)
	}
	if e := close(); e != nil {
		t.Fatal(e)
	}
}
