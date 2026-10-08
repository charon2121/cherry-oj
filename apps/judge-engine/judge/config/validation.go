package config

import (
	"fmt"
	"time"
)

// Validate 把「配错了」挡在启动时，而不是让它伪装成一个合理的判题结论。
//
// 这条教训在这个项目里反复出现：limits 的 cpuNs=0 会让每道题秒 TLE，
// 而返回的结果看起来完全正常。配置也一样——宁可起不来，也别悄悄跑错。
func (c Config) Validate() error {
	if err := c.Logging.Validate(); err != nil {
		return err
	}
	j := c.Judge
	if err := j.Node.Validate(); err != nil {
		return err
	}
	if j.HTTPAddr == "" {
		return fmt.Errorf("judge.httpAddr must not be empty")
	}
	if err := c.Execution.Validate("execution"); err != nil {
		return err
	}
	if j.Testdata.WorkRoot == "" {
		return fmt.Errorf("judge.testdata.workRoot must not be empty")
	}
	if j.Testdata.MaxFileBytes <= 0 || j.Testdata.MaxTotalBytes <= 0 {
		return fmt.Errorf("judge.testdata.maxFileBytes and maxTotalBytes must be positive, got %d and %d",
			j.Testdata.MaxFileBytes, j.Testdata.MaxTotalBytes)
	}
	if j.Testdata.FetchTimeout <= 0 {
		return fmt.Errorf("judge.testdata.fetchTimeout must be positive, got %s", j.Testdata.FetchTimeout)
	}
	if j.ClockRatio <= 0 {
		return fmt.Errorf("judge.clockRatio must be positive, got %d", j.ClockRatio)
	}
	if j.InlineThresholdBytes < 0 {
		return fmt.Errorf("judge.inlineThresholdBytes must not be negative, got %d", j.InlineThresholdBytes)
	}
	if j.OutputExcerptBytes < 0 {
		return fmt.Errorf("judge.outputExcerptBytes must not be negative, got %d", j.OutputExcerptBytes)
	}
	if j.MessageExcerptBytes < 0 {
		return fmt.Errorf("judge.messageExcerptBytes must not be negative, got %d", j.MessageExcerptBytes)
	}
	if j.Output.StdoutMaxBytes <= 0 {
		return fmt.Errorf("judge.output.stdoutMaxBytes must be positive, got %d", j.Output.StdoutMaxBytes)
	}
	if j.Output.StderrMaxBytes <= 0 {
		return fmt.Errorf("judge.output.stderrMaxBytes must be positive, got %d", j.Output.StderrMaxBytes)
	}
	if j.Compile.CPUNs <= 0 || j.Compile.MemoryBytes <= 0 || j.Compile.ClockNs <= 0 {
		return fmt.Errorf("all three judge.compile values must be positive, got %+v", j.Compile)
	}
	// 编译墙钟超过执行层的硬界，每次编译都会被执行器拒绝，表现成一连串 SE；启动时就挡住。
	// 测试点墙钟（显式值或 cpuNs × clockRatio）随请求变化，由 flow 在上传源码前检查。
	if j.Compile.ClockNs > MaxClockNs {
		return fmt.Errorf("judge.compile.clockNs (%s) exceeds the execution wall-clock hard limit (%s)",
			time.Duration(j.Compile.ClockNs), time.Duration(MaxClockNs))
	}
	return nil
}
