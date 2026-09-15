package container

import (
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"sync"

	"cherry-oj/judge-engine/internal/hostexec"
)

func (c *isolatedContainer) PutFile(name string, r io.Reader, mode fs.FileMode) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed || c.attempted {
		return fmt.Errorf("工作区不再接受输入")
	}
	if !hostexec.ValidPath(name) || len(c.inputs) >= 128 {
		return fmt.Errorf("输入路径/数量无效: %q", name)
	}
	for _, f := range c.inputs {
		if f.Path == name {
			return fmt.Errorf("重复输入: %q", name)
		}
	}
	f, n, err := c.spool(r, hostexec.MaxInputBytes-c.bytes)
	if err != nil {
		return err
	}
	c.files = append(c.files, f)
	c.inputs = append(c.inputs, hostexec.Input{Path: name, SizeBytes: n, Executable: mode&0o111 != 0})
	c.bytes += n
	return nil
}

// spool 先取得长度，使 helper 可按声明大小划分连续输入流；limit+1 用于发现超限，
// 不能在上限处伪装 EOF，否则被截断的源码会被当作完整输入交付。
func (c *isolatedContainer) spool(r io.Reader, limit int64) (*os.File, int64, error) {
	if r == nil {
		return nil, 0, fmt.Errorf("输入流为空")
	}
	f, err := os.CreateTemp(c.dir, "data-")
	if err != nil {
		return nil, 0, err
	}
	n, err := io.Copy(f, io.LimitReader(r, limit+1))
	if err == nil && n > limit {
		err = fmt.Errorf("文件总量超过 %d bytes", limit)
	}
	if err == nil {
		_, err = f.Seek(0, io.SeekStart)
	}
	if err != nil {
		return nil, 0, errors.Join(err, f.Close(), os.Remove(f.Name()))
	}
	return f, n, nil
}

func (c *isolatedContainer) GetFile(name string) (io.ReadCloser, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed || c.process == nil {
		return nil, fmt.Errorf("产物尚不可读")
	}
	select {
	case <-c.process.done:
	default:
		return nil, fmt.Errorf("执行尚未完成")
	}
	if c.process.err != nil {
		return nil, fmt.Errorf("回收/传输未确认: %w", c.process.err)
	}
	path, ok := c.outputs[name]
	if !ok {
		return nil, fmt.Errorf("未交付产物: %q", name)
	}
	return os.Open(path)
}

type inputStream struct {
	io.Reader
	files []*os.File
	once  sync.Once
	err   error
}

func (s *inputStream) Close() error {
	s.once.Do(func() {
		for _, f := range s.files {
			s.err = errors.Join(s.err, f.Close())
		}
	})
	return s.err
}

func (c *isolatedContainer) prepareRequest(s Spec) (hostexec.Request, error) {
	r := hostexec.Request{Version: hostexec.Version, Command: s.Command, Env: s.Env, Inputs: c.inputs, Outputs: s.Outputs, Limits: s.Limits}
	if s.Stdin != nil {
		f, n, err := c.spool(s.Stdin, hostexec.MaxInputBytes-c.bytes)
		if err != nil {
			return r, err
		}
		c.files = append(c.files, f)
		r.StdinBytes = n
	}
	if err := r.Validate(); err != nil {
		return r, err
	}
	return r, nil
}

func (c *isolatedContainer) receiveOutput(o hostexec.Output, reader io.Reader) error {
	f, n, e := c.spool(reader, o.SizeBytes)
	if e != nil {
		return e
	}
	closeErr := f.Close()
	if n != o.SizeBytes {
		return errors.Join(io.ErrUnexpectedEOF, closeErr)
	}
	if closeErr != nil {
		return closeErr
	}
	c.outputs[o.Path] = f.Name()
	return nil
}
