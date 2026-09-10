package helper

import (
	"context"
	"errors"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/sandbox/launcher"
)

func testRequest() launcher.Request {
	return launcher.Request{Version: 1, Command: []string{"main"}, Limits: contract.ExplicitLimits(contract.Limits{CPUNs: 1e9, ClockNs: 2e9, MemoryBytes: 64 << 20, MaxProcesses: 32, StdoutMaxBytes: 1024, StderrMaxBytes: 1024})}
}
func fakeServer(t *testing.T, serve func(net.Conn)) string {
	t.Helper()
	// macOS 的 t.TempDir 路径可能超过 sockaddr_un 上限，使用独占短目录。
	dir, err := os.MkdirTemp("/tmp", "cherry-helper-")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := os.RemoveAll(dir); err != nil {
			t.Error(err)
		}
	})
	path := filepath.Join(dir, "s")
	l, err := net.Listen("unix", path)
	if err != nil {
		t.Fatal(err)
	}
	done := make(chan struct{})
	go func() {
		defer close(done)
		c, err := l.Accept()
		if err != nil {
			return
		}
		defer c.Close()
		c.SetDeadline(time.Now().Add(3 * time.Second))
		serve(c)
	}()
	t.Cleanup(func() { l.Close(); <-done })
	return path
}
func TestClientStreamsInputsAndOutputs(t *testing.T) {
	request := testRequest()
	request.Inputs = []launcher.Input{{Path: "main.cpp", SizeBytes: 3}}
	request.StdinBytes = 2
	request.Outputs = []string{"main"}
	socket := fakeServer(t, func(c net.Conn) {
		var r launcher.Request
		if err := launcher.ReadFrame(c, &r, launcher.MaxFrameBytes); err != nil {
			t.Error(err)
			return
		}
		data := make([]byte, r.InputBytes())
		if _, err := io.ReadFull(c, data); err != nil {
			t.Error(err)
			return
		}
		if string(data) != "abcde" {
			t.Error("input ordering")
		}
		if err := launcher.WriteFrame(c, Result{Version: 1, Outputs: []Output{{Path: "main", SizeBytes: 2}}}, 4<<20); err != nil {
			t.Error(err)
			return
		}
		if _, err := io.WriteString(c, "ok"); err != nil {
			t.Error(err)
		}
		if err := launcher.WriteFrame(c, Completion{Version: 1, Complete: true}, 1024); err != nil {
			t.Error(err)
		}
	})
	var got string
	_, err := Call(context.Background(), socket, request, io.NopCloser(strings.NewReader("abcde")), func(o Output, r io.Reader) error { b, e := io.ReadAll(r); got = string(b); return e })
	if err != nil || got != "ok" {
		t.Fatalf("%q %v", got, err)
	}
}
func TestClientEarlyFailureClosesBlockedInput(t *testing.T) {
	socket := fakeServer(t, func(c net.Conn) {
		var r launcher.Request
		if err := launcher.ReadFrame(c, &r, launcher.MaxFrameBytes); err != nil {
			t.Error(err)
			return
		}
		if err := launcher.WriteFrame(c, Result{Version: 1, Reason: "platform", Error: "startup"}, 4<<20); err != nil {
			t.Error(err)
		}
		if err := launcher.WriteFrame(c, Completion{Version: 1, Complete: true}, 1024); err != nil {
			t.Error(err)
		}
	})
	request := testRequest()
	request.StdinBytes = 1
	reader, writer := io.Pipe()
	defer writer.Close()
	res, err := Call(context.Background(), socket, request, reader, nil)
	if err != nil || res.Reason != "platform" {
		t.Fatal(res, err)
	}
	if _, err = writer.Write([]byte{1}); !errors.Is(err, io.ErrClosedPipe) {
		t.Fatal("input was not closed", err)
	}
}
func TestClientRejectsUnrequestedArtifact(t *testing.T) {
	socket := fakeServer(t, func(c net.Conn) {
		var r launcher.Request
		if err := launcher.ReadFrame(c, &r, launcher.MaxFrameBytes); err != nil {
			t.Error(err)
			return
		}
		if err := launcher.WriteFrame(c, Result{Version: 1, Outputs: []Output{{Path: "secret", SizeBytes: 1}}}, 4<<20); err != nil {
			t.Error(err)
		}
	})
	_, err := Call(context.Background(), socket, testRequest(), io.NopCloser(strings.NewReader("")), nil)
	if err == nil {
		t.Fatal("接受未经请求的产物")
	}
}

func TestClientRejectsMissingCleanupAcknowledgement(t *testing.T) {
	socket := fakeServer(t, func(c net.Conn) {
		var r launcher.Request
		if err := launcher.ReadFrame(c, &r, launcher.MaxFrameBytes); err != nil {
			t.Error(err)
			return
		}
		if err := launcher.WriteFrame(c, Result{Version: 1}, 4<<20); err != nil {
			t.Error(err)
		}
	})
	if _, err := Call(context.Background(), socket, testRequest(), io.NopCloser(strings.NewReader("")), nil); err == nil {
		t.Fatal("缺回收完成尾帧仍返回成功")
	}
}
func TestClientCancellationReleasesBlockedInput(t *testing.T) {
	socket := fakeServer(t, func(c net.Conn) {
		var r launcher.Request
		if err := launcher.ReadFrame(c, &r, launcher.MaxFrameBytes); err != nil {
			return
		}
		var b [1]byte
		_, _ = c.Read(b[:])
	})
	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()
	r := testRequest()
	r.StdinBytes = 1
	input, writer := io.Pipe()
	defer writer.Close()
	if _, err := Call(ctx, socket, r, input, nil); err == nil {
		t.Fatal("取消后仍返回成功")
	}
	if _, err := writer.Write([]byte{1}); !errors.Is(err, io.ErrClosedPipe) {
		t.Fatal("取消后输入仍阻塞", err)
	}
}
