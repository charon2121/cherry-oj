package launcher_test

import (
	"bytes"
	"encoding/binary"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/launcher"
)

func request() launcher.Request {
	return launcher.Request{Version: 1, Command: []string{"main"}, Limits: contract.ExplicitLimits(contract.Limits{CPUNs: 1e9, ClockNs: 2e9, MemoryBytes: 64 << 20, MaxProcesses: 32, StdoutMaxBytes: 0, StderrMaxBytes: 0})}
}
func TestRequestRejectsEscapesAndUnboundedData(t *testing.T) {
	for _, path := range []string{"../x", "/etc/passwd", "a/../../b", "a//b", ".stdin", "a/./b", "a\\b", "a\x00b"} {
		t.Run(path, func(t *testing.T) {
			r := request()
			r.Inputs = []launcher.Input{{Path: path, SizeBytes: 1}}
			if r.Validate() == nil {
				t.Fatal("接受不安全路径")
			}
		})
	}
	for name, change := range map[string]func(*launcher.Request){"zero CPU": func(r *launcher.Request) { r.Limits.CPUNs = 0 }, "memory": func(r *launcher.Request) { r.Limits.MemoryBytes = 2 << 30 }, "size": func(r *launcher.Request) {
		r.StdinBytes = launcher.MaxInputBytes
		r.Inputs = []launcher.Input{{Path: "main.cpp", SizeBytes: 1}}
	}, "duplicate": func(r *launcher.Request) { r.Inputs = []launcher.Input{{Path: "x"}, {Path: "x"}} }, "absolute command": func(r *launcher.Request) { r.Command = []string{"/bin/sh"} }, "environment": func(r *launcher.Request) { r.Env = []string{"NO_EQUALS"} }} {
		t.Run(name, func(t *testing.T) {
			r := request()
			change(&r)
			if r.Validate() == nil {
				t.Fatal("接受无效请求")
			}
		})
	}
	r := request()
	r.Inputs = []launcher.Input{{Path: "src/main.cpp", SizeBytes: 3}}
	if err := r.Validate(); err != nil {
		t.Fatal(err)
	}
}
func TestFramePreservesFollowingStream(t *testing.T) {
	var b bytes.Buffer
	r := request()
	if err := launcher.WriteFrame(&b, r, launcher.MaxFrameBytes); err != nil {
		t.Fatal(err)
	}
	b.WriteString("payload\x00bytes")
	var got launcher.Request
	if err := launcher.ReadFrame(&b, &got, launcher.MaxFrameBytes); err != nil {
		t.Fatal(err)
	}
	if b.String() != "payload\x00bytes" {
		t.Fatal("控制解析吞掉输入")
	}
	if err := got.Validate(); err != nil {
		t.Fatal(err)
	}
}
func TestFrameRejectsInvalidWire(t *testing.T) {
	for _, body := range []string{`{"UID":0}`, `{} {}`, `{"Version":1}junk`, ``} {
		var b bytes.Buffer
		var h [4]byte
		binary.BigEndian.PutUint32(h[:], uint32(len(body)))
		b.Write(h[:])
		b.WriteString(body)
		var r launcher.Request
		if err := launcher.ReadFrame(&b, &r, launcher.MaxFrameBytes); err == nil {
			t.Fatalf("接受 %q", body)
		}
	}
	var h [4]byte
	binary.BigEndian.PutUint32(h[:], launcher.MaxFrameBytes+1)
	var r launcher.Request
	if launcher.ReadFrame(bytes.NewReader(h[:]), &r, launcher.MaxFrameBytes) == nil {
		t.Fatal("接受超大帧")
	}
}

func TestCompilerCommandNamesPreservePathBoundary(t *testing.T) {
	for _, name := range []string{"g++", "g++-13", "clang++", "program"} {
		r := request()
		r.Command = []string{name}
		if err := r.Validate(); err != nil {
			t.Fatalf("valid compiler %q: %v", name, err)
		}
	}
	for _, name := range []string{"/usr/bin/g++", "../g++", "g++/x", "g++;sh", "g++ x"} {
		r := request()
		r.Command = []string{name}
		if r.Validate() == nil {
			t.Fatalf("unsafe command %q accepted", name)
		}
	}
}
