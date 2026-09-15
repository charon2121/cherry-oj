// Package client 是本机执行协议的非特权侧实现。
//
// 它只会发起调用、校验响应并接收产物，不包含任何特权操作；helper 的服务端实现
// 不在本包，也不被本包引用。
package client

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"sync"
	"time"

	"cherry-oj/judge-engine/internal/hostexec"
)

const dialTimeout = 3 * time.Second // 本机 socket 建连期限，独立于建连后的会话。

// Call 接管并关闭 input；其 Close 必须能解除 Read 阻塞（本地文件或有界内存流）。
// Call 流式交付输入和产物。consume 必须同步处理每个受限 reader；不能保留 reader 异步读取。
// 对端入口是 helper 的 serveConn。只有 Completion 后的正常 EOF 才表示槽位已归还；
// consume 即使已收到文件，也不能在 Call 成功前对外发布。
func Call(ctx context.Context, socket string, r hostexec.Request, input io.ReadCloser, consume func(hostexec.Output, io.Reader) error) (hostexec.Result, error) {
	var result hostexec.Result
	if input == nil {
		return result, fmt.Errorf("the input stream must not be nil")
	}
	closeInput := sync.OnceValue(input.Close)
	defer closeInput()
	if err := r.Validate(); err != nil {
		return result, errors.Join(err, closeInput())
	}
	conn, err := (&net.Dialer{Timeout: dialTimeout}).DialContext(ctx, "unix", socket)
	if err != nil {
		return result, errors.Join(err, closeInput())
	}
	closeConn := sync.OnceValue(conn.Close)
	defer closeConn()
	stop := context.AfterFunc(ctx, func() { closeConn() })
	defer stop()

	err = conn.SetDeadline(time.Now().Add(hostexec.SessionTimeout))
	if err == nil {
		err = hostexec.WriteFrame(conn, r, hostexec.MaxFrameBytes)
	}
	if err == nil {
		// helper 可能在读完输入前响应失败；上传与接收并行，避免双向堵塞。
		sent := uploadInput(conn, input, r.InputBytes())
		finishUpload := sync.OnceValue(func() error {
			// 必须先解除两端阻塞再等上传，不能单独等待 sent。
			closeConn()
			closeInput()
			return <-sent
		})
		defer finishUpload() // consume 等外部实现 panic 时仍回收上传任务。
		result, err = receiveResult(conn, r.Outputs, consume)
		if err == nil {
			err = awaitCompletion(ctx, conn)
		}
		if uploadErr := finishUpload(); uploadErr != nil && result.Reason == "" {
			err = errors.Join(err, uploadErr)
		}
	}
	stop()
	// 关闭错误属于本次调用结果；文件接收成功并不代表可以发布。
	return result, errors.Join(err, closeConn(), closeInput())
}

func uploadInput(conn net.Conn, input io.Reader, bytes int64) <-chan error {
	sent := make(chan error, 1)
	go func() {
		_, err := io.CopyN(conn, input, bytes)
		if err != nil {
			if unix, ok := conn.(*net.UnixConn); ok {
				unix.CloseWrite()
			}
		}
		sent <- err
	}()
	return sent
}

func receiveResult(conn io.Reader, outputs []string, consume func(hostexec.Output, io.Reader) error) (hostexec.Result, error) {
	var result hostexec.Result
	if err := hostexec.ReadFrame(conn, &result, hostexec.MaxResultFrameBytes); err != nil {
		return result, err
	}
	if result.Version != hostexec.Version || len(result.Outputs) > hostexec.MaxOutputs {
		return result, fmt.Errorf("invalid helper response version or artifact count")
	}
	allowed := map[string]bool{}
	for _, p := range outputs {
		allowed[p] = true
	}
	var total int64
	for _, o := range result.Outputs {
		if !allowed[o.Path] || o.SizeBytes < 0 || o.SizeBytes > hostexec.MaxArtifactBytes-total {
			return result, fmt.Errorf("helper returned an unauthorized or oversized artifact")
		}
		delete(allowed, o.Path)
		total += o.SizeBytes
		// 每个产物必须消费到声明长度；即使调用者只读前缀，也不能让剩余内容冒充下一帧。
		reader := &io.LimitedReader{R: conn, N: o.SizeBytes}
		if consume != nil {
			if err := consume(o, reader); err != nil {
				return result, err
			}
		}
		if _, err := io.Copy(io.Discard, reader); err != nil {
			return result, err
		}
		if reader.N != 0 {
			return result, io.ErrUnexpectedEOF
		}
	}
	return result, nil
}

func awaitCompletion(ctx context.Context, conn io.Reader) error {
	var completion hostexec.Completion
	if err := hostexec.ReadFrame(conn, &completion, hostexec.MaxCompletionFrameBytes); err != nil {
		return err
	}
	if completion.Version != hostexec.Version || !completion.Complete {
		return fmt.Errorf("helper did not confirm complete reclaim and delivery")
	}
	// Completion 确认执行资源和产物回收；正常 EOF 才确认连接收尾、槽位归还。
	// 此读取仍受上面的总期限和 ctx 取消约束。reset 或多余字节不能当作成功。
	var trailing [1]byte
	if n, err := conn.Read(trailing[:]); n != 0 {
		return fmt.Errorf("extra data after the helper completion frame")
	} else if err != io.EOF {
		if err == nil {
			err = io.ErrNoProgress
		}
		return fmt.Errorf("wait for the helper to close the connection: %w", err)
	}
	return ctx.Err()
}
