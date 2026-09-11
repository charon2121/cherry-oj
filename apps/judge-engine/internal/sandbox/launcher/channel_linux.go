package launcher

import (
	"encoding/json"
	"fmt"
	"io"
	"os"

	"golang.org/x/sys/unix"
)

// SocketPair 返回带 CLOEXEC 的 seqpacket 通道。文件接收必须使用 MSG_CMSG_CLOEXEC。
func SocketPair() (*os.File, *os.File, error) {
	f, err := unix.Socketpair(unix.AF_UNIX, unix.SOCK_SEQPACKET|unix.SOCK_CLOEXEC, 0)
	if err != nil {
		return nil, nil, err
	}
	return os.NewFile(uintptr(f[0]), "control-parent"), os.NewFile(uintptr(f[1]), "control-child"), nil
}
func SendEvent(c *os.File, e Event, dir *os.File) error {
	return sendEvent(c, e, dir, unix.SendmsgN)
}

func sendEvent(c *os.File, e Event, dir *os.File, send func(int, []byte, []byte, unix.Sockaddr, int) (int, error)) error {
	e.Version = Version
	b, err := json.Marshal(e)
	if err != nil {
		return err
	}
	for {
		var n int
		var sendErr error
		err := controlFD(c, func(fd int) {
			if dir == nil {
				n, sendErr = send(fd, b, nil, nil, 0)
				return
			}
			// Pin both descriptors for this syscall, not merely their Go objects.
			if err := controlFD(dir, func(dirFD int) {
				n, sendErr = send(fd, b, unix.UnixRights(dirFD), nil, 0)
			}); err != nil {
				sendErr = err
			}
		})
		if err != nil {
			return err
		}
		if sendErr == unix.EINTR && n <= 0 {
			continue
		}
		if sendErr != nil {
			return sendErr
		}
		if n != len(b) {
			return fmt.Errorf("控制消息短写")
		}
		return nil
	}
}
func ReceiveEvent(c *os.File) (Event, *os.File, error) {
	return receiveEvent(c, unix.Recvmsg)
}

func receiveEvent(c *os.File, receive func(int, []byte, []byte, int) (int, int, int, unix.Sockaddr, error)) (Event, *os.File, error) {
	var e Event
	b := make([]byte, 1024)
	oob := make([]byte, unix.CmsgSpace(4*4))
	var n, on, flags int
	var receiveErr error
	for {
		err := controlFD(c, func(fd int) {
			n, on, flags, _, receiveErr = receive(fd, b, oob, unix.MSG_CMSG_CLOEXEC)
		})
		if err != nil {
			return e, nil, err
		}
		if receiveErr != unix.EINTR || n > 0 || on > 0 {
			break
		}
	}
	msgs, parseErr := unix.ParseSocketControlMessage(oob[:on])
	var fds []int
	for _, m := range msgs {
		r, re := unix.ParseUnixRights(&m)
		if re != nil {
			parseErr = re
		}
		fds = append(fds, r...)
	}
	fail := func(err error) (Event, *os.File, error) {
		for _, fd := range fds {
			unix.Close(fd)
		}
		return e, nil, err
	}
	if parseErr != nil {
		return fail(parseErr)
	}
	if receiveErr != nil {
		return fail(receiveErr)
	}
	if n == 0 && len(fds) == 0 {
		return e, nil, io.EOF
	}
	if flags&(unix.MSG_TRUNC|unix.MSG_CTRUNC) != 0 || len(fds) > 1 {
		return fail(fmt.Errorf("控制通道关闭或消息/FD 无效"))
	}
	if err := json.Unmarshal(b[:n], &e); err != nil {
		return fail(err)
	}
	if e.Version != Version {
		return fail(fmt.Errorf("控制消息版本错误"))
	}
	if len(fds) == 1 {
		return e, os.NewFile(uintptr(fds[0]), "workspace"), nil
	}
	return e, nil, nil
}

// controlFD holds an os.File reference during one syscall. Release it between
// EINTR attempts so Close is observed before reacquiring the descriptor; a bare
// cached Fd could instead refer to a different file after concurrent Close.
// These control sockets are blocking: helper cancellation/startup/wall limits
// wake I/O with shutdown(SHUT_RDWR) before Close; the init is also group-killed.
// Do not replace that cancellation chain with Close alone or restart its timers.
func controlFD(f *os.File, call func(int)) error {
	raw, err := f.SyscallConn()
	if err != nil {
		return err
	}
	return raw.Control(func(fd uintptr) { call(int(fd)) })
}
