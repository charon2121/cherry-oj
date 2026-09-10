package runner

import "bytes"

// max 已由入口归一化；0严格表示不能保存任何输出。
type capWriter struct {
	buf          bytes.Buffer
	max, current int64
	overflow     bool
	onOverflow   func()
}

func newCapWriter(max int64) *capWriter { return &capWriter{max: max} }
func (w *capWriter) Write(p []byte) (int, error) {
	remain := w.max - w.current
	if int64(len(p)) > remain && !w.overflow {
		w.overflow = true
		if w.onOverflow != nil {
			w.onOverflow()
		}
	}
	if remain > 0 {
		n := min(int64(len(p)), remain)
		w.buf.Write(p[:n])
		w.current += n
	}
	return len(p), nil
}
