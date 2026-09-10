// 使用内部包以控制未导出的连接收尾边界；不启动特权执行器或加入生产测试开关。
package helper

import (
	"context"
	"errors"
	"io"
	"net"
	"strings"
	"testing"
	"time"

	"cherry-oj/judge-engine/internal/sandbox/launcher"
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
	if err := launcher.WriteFrame(c, testRequest(), launcher.MaxFrameBytes); err != nil {
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
	var result Result
	if err := launcher.ReadFrame(c, &result, 4<<20); err != nil {
		t.Fatal(err)
	}
	var completion Completion
	if err := launcher.ReadFrame(c, &completion, 1024); err != nil || !completion.Complete {
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
		var request launcher.Request
		if err := launcher.ReadFrame(c, &request, launcher.MaxFrameBytes); err != nil {
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
		if err := launcher.WriteFrame(c, Result{Version: launcher.Version}, 4<<20); err != nil {
			t.Error(err)
		}
		if err := launcher.WriteFrame(c, Completion{Version: launcher.Version, Complete: true}, 1024); err != nil {
			t.Error(err)
		}
	})
	request := testRequest()
	request.StdinBytes = 1
	result, err := Call(context.Background(), socket, request, io.NopCloser(strings.NewReader("x")), nil)
	if result.Version != launcher.Version || !errors.Is(err, unix.ECONNRESET) {
		t.Fatalf("reset 被当作正常完成或测试未交付响应: result=%+v err=%v", result, err)
	}
}
