package runner

import "testing"

// capWriter 的边界：集成测试给的限额离上限很远，撞不到这些用例。
func TestCapWriterBoundary(t *testing.T) {
	tests := []struct {
		name     string
		max      int64
		write    string
		wantBuf  string
		wantOver bool
	}{
		{"恰好写满不算溢出", 5, "hello", "hello", false},
		{"多一个字节才算溢出", 5, "hello!", "hello", true},
		{"没设上限走默认", 0, "hi", "hi", false},
		{"负数上限也走默认", -1, "hi", "hi", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			w := newCapWriter(tt.max)

			n, err := w.Write([]byte(tt.write))
			// 无论截没截断，都要报「全写成功」，否则子进程写管道会提前崩
			if err != nil || n != len(tt.write) {
				t.Fatalf("Write=(%d,%v) want (%d,nil)", n, err, len(tt.write))
			}

			if w.buf.String() != tt.wantBuf {
				t.Errorf("buf=%q want %q", w.buf.String(), tt.wantBuf)
			}
			if w.overflow != tt.wantOver {
				t.Errorf("overflow=%v want %v", w.overflow, tt.wantOver)
			}
		})
	}
}

// 写满之后继续写，仍要算溢出
func TestCapWriterOverflowAfterFull(t *testing.T) {
	w := newCapWriter(5)

	if _, err := w.Write([]byte("hello")); err != nil {
		t.Fatal(err)
	}
	if w.overflow {
		t.Fatalf("写满还没溢出，overflow 不该为 true")
	}

	if _, err := w.Write([]byte("x")); err != nil {
		t.Fatal(err)
	}
	if !w.overflow {
		t.Errorf("写满后继续写应算溢出")
	}
	if w.buf.String() != "hello" {
		t.Errorf("buf=%q want %q", w.buf.String(), "hello")
	}
}
