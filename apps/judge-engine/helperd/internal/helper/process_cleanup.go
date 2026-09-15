package helper

import (
	"context"
	"errors"
	"os"
)

// Wait 先关闭写端、解除控制读取，再有界等待所有后台任务；不能先释放它们仍使用的目录。
func (p *isolatedProcess) Wait(ctx context.Context) (processCompletion, error) {
	if p.waitDone {
		return p.completion, p.waitResult
	}
	p.waitResult = p.waitForInitAndIO(ctx)
	p.completion.stopped = p.initStopped
	p.waitDone = true
	return p.completion, p.waitResult
}
func (p *isolatedProcess) waitForInitAndIO(ctx context.Context) error {
	result := wrapError("等待 init", p.waitErr)
	result = errors.Join(result, wrapError("关闭控制通道", p.shutdownControl()))
	result = errors.Join(result, closeFiles(p.childControl, p.dataR, p.dataW, p.outW, p.errW, p.lifeR, p.lifeW))
	if p.inputFinished != nil {
		select {
		case <-p.inputFinished:
		case <-ctx.Done():
			result = errors.Join(result, wrapError("等待输入结束", ctx.Err()))
		}
	}
	if p.initExited != nil && !p.initStopped {
		select {
		case err := <-p.initExited:
			p.completion.waitErr = err
			p.initStopped = isProcessExit(err)
			// Stop 主动终止 init，ExitError 是退出事实；其他错误意味着不能确认 wait 成功。
			if !isProcessExit(err) {
				result = errors.Join(result, wrapError("等待 init", err))
			}
		case <-ctx.Done():
			result = errors.Join(result, wrapError("等待 init", ctx.Err()))
		}
	}
	result = errors.Join(result, p.collectCapture(ctx, p.stdoutDone, p.outR, true), p.collectCapture(ctx, p.stderrDone, p.errR, false))
	return errors.Join(result, p.drainEvents(ctx))
}

func (p *isolatedProcess) collectCapture(ctx context.Context, ch <-chan captureResult, file *ownedFile, stdout bool) error {
	if ch == nil {
		return nil
	}
	var capture captureResult
	select {
	case capture = <-ch:
	case <-ctx.Done():
		return errors.Join(wrapError("等待输出结束", ctx.Err()), file.Close())
	}
	if stdout {
		p.completion.stdout = capture.bytes
	} else {
		p.completion.stderr = capture.bytes
	}
	p.completion.outputExceeded = p.completion.outputExceeded || capture.exceeded
	return wrapError("读取输出", capture.err)
}

// drainEvents 接管监督提前结束后尚未消费的目录 FD；仅关闭 channel 不会释放这些句柄。
func (p *isolatedProcess) drainEvents(ctx context.Context) error {
	if p.events == nil {
		return nil
	}
	var result error
	for {
		select {
		case ev, ok := <-p.events:
			if !ok {
				return result
			}
			result = errors.Join(result, ev.dir.Close())
		case <-ctx.Done():
			return errors.Join(result, wrapError("等待控制接收结束", ctx.Err()))
		}
	}
}

// Close 只释放宿主句柄；不能代替停组及 Wait。
func (p *isolatedProcess) Close() error {
	if p.closeDone {
		return p.closeErr
	}
	p.closeDone = true
	p.closeErr = p.cgroupFD.Close()
	if p.workspace != nil {
		p.closeErr = errors.Join(p.closeErr, p.workspace.Close())
		p.workspace = nil
	}
	p.closeErr = errors.Join(p.closeErr, closeFiles(p.control, p.outR, p.errR))
	return p.closeErr
}
func (p *isolatedProcess) RemoveMountpoint() error {
	if p.mountpoint == "" {
		return nil
	}
	err := os.Remove(p.mountpoint)
	if err == nil {
		p.mountpoint = ""
	}
	return wrapError("删除空挂载目录", err)
}
