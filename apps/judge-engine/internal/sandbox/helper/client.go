package helper

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"sync"
	"time"

	"cherry-oj/judge-engine/internal/sandbox/launcher"
)

// Call 接管并关闭 input；其 Close 必须能解除 Read 阻塞（本地文件或有界内存流）。
// Call 流式交付输入和产物。consume 必须同步处理每个受限 reader；不能保留 reader 异步读取。
// 上层 TASK-097 负责解析 store ref、默认限额及状态映射；此处没有判题语义。
func Call(ctx context.Context, socket string, r launcher.Request, input io.ReadCloser, consume func(Output, io.Reader) error) (result Result, resultErr error) {
	if input == nil {
		return result, fmt.Errorf("输入流不能为空")
	}
	closeInput := sync.OnceValue(input.Close)
	defer func() { resultErr = errors.Join(resultErr, closeInput()) }()
	if err := r.Validate(); err != nil {
		return result, err
	}
	d := net.Dialer{Timeout: 3 * time.Second}
	conn, err := d.DialContext(ctx, "unix", socket)
	if err != nil {
		return result, err
	}
	closeConn := sync.OnceValue(conn.Close)
	defer func() { resultErr = errors.Join(resultErr, closeConn()) }()
	stop := context.AfterFunc(ctx, func() { closeConn() })
	defer stop()
	if err = conn.SetDeadline(time.Now().Add(150 * time.Second)); err != nil {
		return result, err
	}
	if err = launcher.WriteFrame(conn, r, launcher.MaxFrameBytes); err != nil {
		return result, err
	}
	// 读响应与写输入并行：启动早期失败或低预算时 helper 可以提前响应，避免双向堵塞。
	sent := make(chan error, 1)
	go func() {
		_, err := io.CopyN(conn, input, r.InputBytes())
		if err != nil {
			if u, ok := conn.(*net.UnixConn); ok {
				u.CloseWrite()
			}
		}
		sent <- err
	}()
	defer func() {
		// 主 defer 保留关闭错误；这里先中断阻塞上传，再等待线程退出。
		closeConn()
		closeInput()
		e := <-sent
		if e != nil && result.Reason == "" {
			resultErr = errors.Join(resultErr, e)
		}
	}()
	if err = launcher.ReadFrame(conn, &result, 4<<20); err != nil {
		return result, err
	}
	if result.Version != launcher.Version || len(result.Outputs) > 128 {
		return result, fmt.Errorf("helper 响应版本/产物数无效")
	}
	allowed := map[string]bool{}
	for _, p := range r.Outputs {
		allowed[p] = true
	}
	var total int64
	for _, o := range result.Outputs {
		if !allowed[o.Path] || o.SizeBytes < 0 || o.SizeBytes > launcher.MaxArtifactBytes-total {
			return result, fmt.Errorf("helper 返回未授权或超大产物")
		}
		delete(allowed, o.Path)
		total += o.SizeBytes
		reader := &io.LimitedReader{R: conn, N: o.SizeBytes}
		if consume != nil {
			if err = consume(o, reader); err != nil {
				return result, err
			}
		}
		if _, err = io.Copy(io.Discard, reader); err != nil {
			return result, err
		}
		if reader.N != 0 {
			return result, io.ErrUnexpectedEOF
		}
	}
	var completion Completion
	if err = launcher.ReadFrame(conn, &completion, 1024); err != nil {
		return result, err
	}
	if completion.Version != launcher.Version || !completion.Complete {
		return result, fmt.Errorf("helper 没有确认完整回收与交付")
	}
	// 提前失败时关闭 socket，打断仍在向 helper 写入的线程；输入源本身须遵循调用者 ctx。
	closeConn()

	return result, nil
}
