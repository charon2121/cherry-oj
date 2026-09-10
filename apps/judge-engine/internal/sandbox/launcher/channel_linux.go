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
	e.Version = Version
	b, err := json.Marshal(e)
	if err != nil {
		return err
	}
	var rights []byte
	if dir != nil {
		rights = unix.UnixRights(int(dir.Fd()))
	}
	n, err := unix.SendmsgN(int(c.Fd()), b, rights, nil, 0)
	if err != nil {
		return err
	}
	if n != len(b) {
		return fmt.Errorf("控制消息短写")
	}
	return nil
}
func ReceiveEvent(c *os.File) (Event, *os.File, error) {
	var e Event
	b := make([]byte, 1024)
	oob := make([]byte, unix.CmsgSpace(4*4))
	n, on, flags, _, err := unix.Recvmsg(int(c.Fd()), b, oob, unix.MSG_CMSG_CLOEXEC)
	if err != nil {
		return e, nil, err
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
	if n == 0 && len(fds) == 0 {
		return e, nil, io.EOF
	}
	if flags&(unix.MSG_TRUNC|unix.MSG_CTRUNC) != 0 || len(fds) > 1 {
		return fail(fmt.Errorf("控制通道关闭或消息/FD 无效"))
	}
	if err = json.Unmarshal(b[:n], &e); err != nil {
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
