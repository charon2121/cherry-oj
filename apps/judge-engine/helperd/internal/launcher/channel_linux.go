//go:build linux && amd64

package launcher

import (
	"encoding/json"
	"fmt"
	"io"
	"os"

	"golang.org/x/sys/unix"

	"cherry-oj/judge-engine/internal/hostexec"
)

// 接收缓冲有意大于协议允许的一个 FD，以便发现并关闭多传的句柄。
// SCM_RIGHTS 每个 FD 用 int32 表示；容量沿用现值，不改变截断与拒绝规则。
const (
	maxEventBytes      = 1024
	receivedFDCapacity = 4
	rightsFDBytes      = 4
)

// SocketPair 返回带 CLOEXEC 的 seqpacket 通道；消息边界使事件和附带 FD 不会被流式读取混淆。
// 两端由调用者分别关闭；接收 FD 仍须 MSG_CMSG_CLOEXEC，发送端标志不会自动继承。
func SocketPair() (*os.File, *os.File, error) {
	f, err := unix.Socketpair(unix.AF_UNIX, unix.SOCK_SEQPACKET|unix.SOCK_CLOEXEC, 0)
	if err != nil {
		return nil, nil, err
	}
	return os.NewFile(uintptr(f[0]), "control-parent"), os.NewFile(uintptr(f[1]), "control-child"), nil
}

// SendEvent 传递 dir 的内核引用，不转移发送方的关闭责任；接收方拥有独立 FD。
func SendEvent(c *os.File, e Event, dir *os.File) error {
	return sendEvent(c, e, dir, unix.SendmsgN)
}

func sendEvent(c *os.File, e Event, dir *os.File, send func(int, []byte, []byte, unix.Sockaddr, int) (int, error)) error {
	e.Version = hostexec.Version
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
			// 同时固定通道和目录的底层 FD，避免并发 Close 后复用到别的文件。
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
			return fmt.Errorf("short write on control message")
		}
		return nil
	}
}

// ReceiveEvent 成功时把收到的目录 FD 交给调用者；校验失败则关闭本次接收的全部 FD。
func ReceiveEvent(c *os.File) (Event, *os.File, error) {
	return receiveEvent(c, unix.Recvmsg)
}

func receiveEvent(c *os.File, receive func(int, []byte, []byte, int) (int, int, int, unix.Sockaddr, error)) (Event, *os.File, error) {
	var e Event
	b := make([]byte, maxEventBytes)
	oob := make([]byte, unix.CmsgSpace(receivedFDCapacity*rightsFDBytes))
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
	// 即使消息截断或 syscall 报错，内核也可能已经交付部分 FD；先解析以便失败路径关闭。
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
		return fail(fmt.Errorf("control channel closed, or invalid message/FD"))
	}
	if err := json.Unmarshal(b[:n], &e); err != nil {
		return fail(err)
	}
	if e.Version != hostexec.Version {
		return fail(fmt.Errorf("wrong control message version"))
	}
	if len(fds) == 1 {
		return e, os.NewFile(uintptr(fds[0]), "workspace"), nil
	}
	return e, nil, nil
}

// controlFD 在一次 syscall 期间固定 os.File 的底层句柄；EINTR 后重新取得引用，
// 才能观察并发 Close，避免使用已经被复用的整数 FD。
// 通道为阻塞 socket，回收通过 shutdown(SHUT_RDWR) 唤醒 I/O，再 Close；
// 只调用 Close 可能仍等着正在持有引用的读取，不能删掉 shutdown 或重置监督计时。
func controlFD(f *os.File, call func(int)) error {
	raw, err := f.SyscallConn()
	if err != nil {
		return err
	}
	return raw.Control(func(fd uintptr) { call(int(fd)) })
}
