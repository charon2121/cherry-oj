// 白盒测试需验证helper不调用带缓存FD的可选复制快路径。
package helper

import (
	"bytes"
	"errors"
	"io"
	"strings"
	"testing"
)

type copySource struct{ io.Reader }

func (copySource) WriteTo(io.Writer) (int64, error) { panic("unexpected WriterTo") }

type copyTarget struct{ bytes.Buffer }

func (*copyTarget) ReadFrom(io.Reader) (int64, error) { panic("unexpected ReaderFrom") }

type brokenWriter struct{}

func (brokenWriter) Write([]byte) (int, error) { return 0, io.ErrClosedPipe }
func TestCopyInputOwnsNoCachedFastPath(t *testing.T) {
	var dst copyTarget
	if err := copyInput(&dst, copySource{strings.NewReader("payload-tail")}, 7); err != nil {
		t.Fatal(err)
	}
	if dst.String() != "payload" {
		t.Fatalf("got %q", dst.String())
	}
	if err := copyInput(io.Discard, strings.NewReader("short"), 6); !errors.Is(err, io.EOF) {
		t.Fatalf("short=%v", err)
	}
	if err := copyInput(brokenWriter{}, strings.NewReader("x"), 1); !errors.Is(err, io.ErrClosedPipe) {
		t.Fatalf("write=%v", err)
	}
}
