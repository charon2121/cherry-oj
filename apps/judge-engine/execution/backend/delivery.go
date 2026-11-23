package backend

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"syscall"
)

// deliverStreams 把 out/stdout 与 out/stderr 交给调用方；长度必须与事实一致且不超过请求的上限。
func deliverStreams(dir string, j Job, r executorFacts) error {
	for _, s := range []struct {
		name  string
		size  int64
		limit int64
		w     io.Writer
	}{{"stdout", r.StdoutBytes, j.Limits.StdoutMaxBytes, j.Stdout}, {"stderr", r.StderrBytes, j.Limits.StderrMaxBytes, j.Stderr}} {
		if s.size > s.limit {
			return fmt.Errorf("sandbox executor %s exceeds the requested limit", s.name)
		}
		data, err := os.ReadFile(filepath.Join(dir, "out", s.name))
		if err != nil {
			return err
		}
		if int64(len(data)) != s.size {
			return fmt.Errorf("sandbox executor %s length differs from its facts", s.name)
		}
		if s.w != nil {
			if _, err := s.w.Write(data); err != nil {
				return err
			}
		}
	}
	return nil
}

// deliverOutputs 逐个交付产物。产物必须是这次请求声明过的，长度与事实一致。
func deliverOutputs(dir string, j Job, r executorFacts, facts Facts, sink OutputSink) error {
	var total int64
	for _, o := range r.Outputs {
		if o.Index < 0 || o.Index >= len(j.Outputs) || j.Outputs[o.Index] != o.Path || o.SizeBytes < 0 || o.SizeBytes > MaxArtifactBytes-total {
			return fmt.Errorf("sandbox executor delivered an unrequested or oversized artifact: %q", o.Path)
		}
		total += o.SizeBytes
		if sink == nil {
			continue
		}
		if err := deliverOutput(filepath.Join(dir, "out", "artifact-"+strconv.Itoa(o.Index)), o, facts, sink); err != nil {
			return err
		}
	}
	return nil
}

func deliverOutput(path string, o executorArtifact, facts Facts, sink OutputSink) error {
	f, err := os.OpenFile(path, os.O_RDONLY|syscall.O_NOFOLLOW, 0)
	if err != nil {
		return err
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() || info.Size() != o.SizeBytes {
		return fmt.Errorf("sandbox executor artifact %q does not match its facts", o.Path)
	}
	return sink(facts, o.Path, io.LimitReader(f, o.SizeBytes))
}
