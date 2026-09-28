//go:build linux && amd64

// 使用内部包以控制未导出的连接收尾边界；不启动特权执行器或加入生产测试开关。
package daemon

import (
	"bytes"
	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/internal/hostexec/client"
	"context"
	"errors"
	"io"
	"log/slog"
	"net"
	"os"
	"strings"
	"testing"
	"time"

	"golang.org/x/sys/unix"
)

type observedConnection struct {
	net.Conn
	beforeClose func()
}

func (c observedConnection) Close() error {
	c.beforeClose()
	return c.Conn.Close()
}

func TestConnectionEOFReturnsCleanSlot(t *testing.T) {
	slots := availableSlots(1)
	slot := <-slots
	delivered := make(chan struct{})
	release := make(chan struct{})
	observed := make(chan bool, 1)
	socket := fakeServer(t, func(c net.Conn) {
		cleaned := false
		conn := observedConnection{Conn: c, beforeClose: func() {
			// 在内核可向客户端交付 EOF 之前，模拟下一连接同步取得唯一槽位。
			select {
			case next := <-slots:
				observed <- cleaned && next == slot
				slots <- next
			default:
				observed <- false
			}
		}}
		serveInSlot(conn, slots, slot, func() {
			defer func() { cleaned = true }()
			writeTestCompletion(t, c)
			close(delivered)
			<-release
		})
	})
	c, err := net.Dial("unix", socket)
	if err != nil {
		close(release)
		t.Fatal(err)
	}
	defer c.Close()
	if err := c.SetDeadline(time.Now().Add(3 * time.Second)); err != nil {
		close(release)
		t.Fatal(err)
	}
	if err := hostexec.WriteFrame(c, testRequest(), hostexec.MaxFrameBytes); err != nil {
		close(release)
		t.Fatal(err)
	}
	<-delivered
	select {
	case next := <-slots:
		slots <- next
		t.Error("交付/清理尚未结束，槽位已被归还")
	default:
	}
	close(release)
	var result hostexec.Result
	if err := hostexec.ReadFrame(c, &result, 4<<20); err != nil {
		t.Fatal(err)
	}
	var completion hostexec.Completion
	if err := hostexec.ReadFrame(c, &completion, 1024); err != nil || !completion.Complete {
		t.Fatal(completion, err)
	}
	var b [1]byte
	if n, err := c.Read(b[:]); n != 0 || err != io.EOF {
		t.Fatalf("没有正常 EOF: n=%d err=%v", n, err)
	}
	if !<-observed {
		t.Fatal("EOF 前下一连接未能取得已经清理的槽位")
	}
}

func TestSlowDeliveryHoldsSlotUntilDeadline(t *testing.T) {
	server, client := net.Pipe()
	defer client.Close()
	defer server.Close()
	slots := availableSlots(1)
	slot := <-slots
	started := make(chan struct{})
	write := make(chan struct{})
	done := make(chan error, 1)
	go func() {
		var err error
		serveInSlot(server, slots, slot, func() {
			close(started)
			<-write
			if err = server.SetWriteDeadline(time.Now().Add(100 * time.Millisecond)); err != nil {
				return
			}
			_, err = server.Write([]byte{1}) // 对端不读，写入只能由期限中断。
		})
		done <- err
	}()
	<-started
	select {
	case next := <-slots:
		slots <- next
		t.Error("慢读者在交付结束前释放了容量")
	default:
	}
	close(write)
	select {
	case err := <-done:
		var timeout net.Error
		if !errors.As(err, &timeout) || !timeout.Timeout() {
			t.Fatalf("写入未被期限中断: %v", err)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("慢读者占槽没有结束")
	}
	select {
	case next := <-slots:
		if next != slot {
			t.Fatal("归还了错误槽位")
		}
	default:
		t.Fatal("写超时后没有归还容量")
	}
	if len(slots) != 0 {
		t.Fatal("槽位被重复归还")
	}
}

func TestClientRejectsResetAfterCompletion(t *testing.T) {
	socket := fakeServer(t, func(c net.Conn) {
		var request hostexec.Request
		if err := hostexec.ReadFrame(c, &request, hostexec.MaxFrameBytes); err != nil {
			t.Error(err)
			return
		}
		raw, err := c.(*net.UnixConn).SyscallConn()
		if err != nil {
			t.Error(err)
			return
		}
		// 等待一个输入字节，但只 PEEK：Linux 在带未消费输入的 socket 关闭时返回 reset。
		var peekErr error
		if err := raw.Read(func(fd uintptr) bool {
			var b [1]byte
			_, _, peekErr = unix.Recvfrom(int(fd), b[:], unix.MSG_PEEK|unix.MSG_DONTWAIT)
			return peekErr != unix.EAGAIN
		}); err != nil || peekErr != nil {
			t.Error(err, peekErr)
			return
		}
		if err := hostexec.WriteFrame(c, hostexec.Result{Version: hostexec.Version}, 4<<20); err != nil {
			t.Error(err)
		}
		if err := hostexec.WriteFrame(c, hostexec.Completion{Version: hostexec.Version, Complete: true}, 1024); err != nil {
			t.Error(err)
		}
	})
	request := testRequest()
	request.StdinBytes = 1
	result, err := client.Call(context.Background(), socket, request, io.NopCloser(strings.NewReader("x")), nil)
	if result.Version != hostexec.Version || !errors.Is(err, unix.ECONNRESET) {
		t.Fatalf("reset 被当作正常完成或测试未交付响应: result=%+v err=%v", result, err)
	}
}

// 协议错误只关这一条连接、不停服，但必须留下记录：客户端只看得到 EOF，
// 日志是区分「请求不合法」与其他断连的唯一依据；请求内容不能进日志。
func TestInvalidRequestIsLoggedWithoutStoppingService(t *testing.T) {
	fds, err := unix.Socketpair(unix.AF_UNIX, unix.SOCK_STREAM|unix.SOCK_CLOEXEC, 0)
	if err != nil {
		t.Fatal(err)
	}
	unixConn := func(fd int) *net.UnixConn {
		f := os.NewFile(uintptr(fd), "test-socket")
		defer f.Close()
		c, err := net.FileConn(f)
		if err != nil {
			t.Fatal(err)
		}
		return c.(*net.UnixConn)
	}
	server, peer := unixConn(fds[0]), unixConn(fds[1])
	defer server.Close()
	defer peer.Close()
	secret := testRequest()
	secret.Version = 99 // 解码成功但校验失败
	secret.Env = []string{"TOKEN=do-not-log"}
	if err := hostexec.WriteFrame(peer, secret, hostexec.MaxFrameBytes); err != nil {
		t.Fatal(err)
	}
	var logs bytes.Buffer
	s := &service{log: slog.New(slog.NewJSONHandler(&logs, nil))}
	if fatal := s.serveConn(context.Background(), server, 2); fatal != nil {
		t.Fatalf("协议错误不应停服: %v", fatal)
	}
	out := logs.String()
	if !strings.Contains(out, `"msg":"isolator.request.invalid"`) || !strings.Contains(out, `"slot":2`) {
		t.Fatalf("没有记录协议错误: %s", out)
	}
	if strings.Contains(out, "do-not-log") {
		t.Fatalf("请求内容进入了日志: %s", out)
	}
}
