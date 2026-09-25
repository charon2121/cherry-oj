//go:build linux && amd64

// 白盒用于验证有界 drain 的字节保留和非阻塞溢出信号。
package execution

import (
	"bytes"
	"io"
	"testing"
)

func TestCaptureBoundsAndDrains(t *testing.T) {
	for _, limit := range []int64{0, 1, 1024} {
		ch := make(chan struct{}, 1)
		w := &boundedCapture{limit: limit, overflow: ch}
		src := bytes.NewReader(bytes.Repeat([]byte{'x'}, 8192))
		n, err := io.Copy(w, src)
		if err != nil || n != 8192 || src.Len() != 0 {
			t.Fatalf("未 drain: %d %v", n, err)
		}
		if int64(len(w.data)) != limit || !w.exceeded {
			t.Fatal("保存越界或遗漏溢出")
		}
		select {
		case <-ch:
		default:
			t.Fatal("没有溢出信号")
		}
	}
	ch := make(chan struct{}, 1)
	w := &boundedCapture{limit: 0, overflow: ch}
	if _, err := w.Write(nil); err != nil || w.exceeded {
		t.Fatal("零输出被误判")
	}
}
