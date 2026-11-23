package backend

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"syscall"
)

// toolchainPattern 与执行器、语言编号的格式一致。
var toolchainPattern = regexp.MustCompile(`^[a-z][a-z0-9-]{0,31}$`)

// writeRequest 按执行器的请求格式写 box：NUL 结尾的 key=value 记录，输入按序号放进 in/。
func writeRequest(dir string, j Job) error {
	var spec bytes.Buffer
	record := func(k, v string) error {
		if strings.ContainsRune(v, 0) {
			return fmt.Errorf("%s contains NUL", k)
		}
		spec.WriteString(k + "=" + v + "\x00")
		return nil
	}
	// 工具链只给语言编号，由执行器在受信目录下解析；格式不对就别交给执行器。
	if !toolchainPattern.MatchString(j.Toolchain) {
		return fmt.Errorf("invalid toolchain: %q", j.Toolchain)
	}
	if err := record("toolchain", j.Toolchain); err != nil {
		return err
	}
	for _, a := range j.Command {
		if err := record("arg", a); err != nil {
			return err
		}
	}
	for _, v := range j.Env {
		if err := record("env", v); err != nil {
			return err
		}
	}
	var total int64
	for i, in := range j.Inputs {
		if !ValidPath(in.Name) {
			return fmt.Errorf("invalid input path: %q", in.Name)
		}
		n, err := spool(filepath.Join(dir, "in", strconv.Itoa(i)), in.Reader, MaxInputBytes-total)
		if err != nil {
			return err
		}
		total += n
		flag := "0"
		if in.Executable {
			flag = "1"
		}
		spec.WriteString("input=" + flag + ":" + in.Name + "\x00")
	}
	var stdin io.Reader = strings.NewReader("")
	if j.Stdin != nil {
		stdin = j.Stdin.Reader
	}
	if _, err := spool(filepath.Join(dir, "stdin"), stdin, MaxInputBytes-total); err != nil {
		return err
	}
	for _, o := range j.Outputs {
		if !ValidPath(o) {
			return fmt.Errorf("invalid artifact path: %q", o)
		}
		spec.WriteString("output=" + o + "\x00")
	}
	l := j.Limits
	for _, kv := range []struct {
		k string
		v int64
	}{{"cpu_ns", l.CPUNs}, {"clock_ns", l.ClockNs}, {"memory_bytes", l.MemoryBytes},
		{"max_processes", int64(l.MaxProcesses)}, {"stdout_max_bytes", l.StdoutMaxBytes},
		{"stderr_max_bytes", l.StderrMaxBytes}} {
		spec.WriteString(kv.k + "=" + strconv.FormatInt(kv.v, 10) + "\x00")
	}
	return os.WriteFile(filepath.Join(dir, "spec"), spec.Bytes(), 0o600)
}

// spool 写入不超过 limit 字节；limit+1 用于发现超限，不能在上限处伪装 EOF，
// 否则被截断的源码会被当作完整输入交付。
func spool(path string, r io.Reader, limit int64) (int64, error) {
	if r == nil {
		return 0, fmt.Errorf("the input stream is nil")
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY|syscall.O_NOFOLLOW, 0o600)
	if err != nil {
		return 0, err
	}
	n, err := io.Copy(f, io.LimitReader(r, limit+1))
	if err == nil && n > limit {
		err = fmt.Errorf("total file size exceeds %d bytes", limit)
	}
	return n, errors.Join(err, f.Close())
}
