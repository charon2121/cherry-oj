//go:build linux && amd64

package execution

// boundedCapture 持续 drain，但只保存上限内的字节；提前停读会让子进程堵在写管道。
// overflow 只需唤醒监督者一次，非阻塞通知避免收集任务反过来等监督者。
type boundedCapture struct {
	data     []byte
	limit    int64
	overflow chan<- struct{}
	exceeded bool
}

func (w *boundedCapture) Write(b []byte) (int, error) {
	n := len(b)
	remaining := w.limit - int64(len(w.data))
	keep := int64(n)
	if keep > remaining {
		keep = remaining
		w.exceeded = true
		select {
		case w.overflow <- struct{}{}:
		default:
		}
	}
	w.data = append(w.data, b[:int(keep)]...)
	return n, nil
}
