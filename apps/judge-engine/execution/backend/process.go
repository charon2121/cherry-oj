package backend

import (
	"bytes"
	"context"
	"errors"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"sync"
	"time"
)

type executorOutput struct {
	stdout []byte
	stderr string
}

// run 运行执行器，并在 stdin 上接一根取消管道：ctx 取消时关闭写端，执行器随即停组并如实报告。
// 不直接杀死执行器——那样它来不及回收执行组。
func (e *Executor) run(ctx context.Context, box int) (executorOutput, int, error) {
	read, write, err := os.Pipe()
	if err != nil {
		return executorOutput{}, 0, err
	}
	var stdout, stderr limitedBuffer
	stdout.max, stderr.max = 64<<10, 4<<10
	cmd := exec.Command(e.binary, "--box", strconv.Itoa(box))
	cmd.Stdin, cmd.Stdout, cmd.Stderr, cmd.Env = read, &stdout, &stderr, []string{}
	// 执行器被杀时，若有后代仍持有它的 stdout/stderr，Wait 会一直等管道关闭；
	// 进程退出后最多再等这么久，之后强制关闭管道，结果按未确认回收处理。
	cmd.WaitDelay = 5 * time.Second
	err = cmd.Start()
	read.Close()
	if err != nil {
		write.Close()
		return executorOutput{}, 0, err
	}
	var once sync.Once
	cancel := func() { once.Do(func() { write.Close() }) }
	done := make(chan struct{})
	go func() {
		select {
		case <-ctx.Done():
			cancel()
		case <-done:
		}
	}()
	err = cmd.Wait()
	close(done)
	cancel()
	out := executorOutput{stdout: stdout.Bytes(), stderr: strings.TrimSpace(stderr.String())}
	var exit *exec.ExitError
	if errors.As(err, &exit) && exit.ExitCode() >= 0 {
		return out, exit.ExitCode(), nil
	}
	return out, 0, err
}

// limitedBuffer 只保留前 max 字节，防止执行器异常输出撑大内存。
type limitedBuffer struct {
	bytes.Buffer
	max int
}

func (b *limitedBuffer) Write(p []byte) (int, error) {
	if room := b.max - b.Len(); room > 0 {
		b.Buffer.Write(p[:min(len(p), room)])
	}
	return len(p), nil
}
