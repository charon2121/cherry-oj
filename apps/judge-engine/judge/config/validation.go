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
	if j.SandboxURL == "" {
		return fmt.Errorf("judge.sandboxURL must not be empty")
	}
	if j.SandboxTimeout <= 0 {
		return fmt.Errorf("judge.sandboxTimeout must be positive, got %s", j.SandboxTimeout)
	}
	if j.EnvironmentFingerprint == "" {
		return fmt.Errorf("judge.environmentFingerprint must not be empty")
	}
	if j.TestdataRoot == "" {
		return fmt.Errorf("judge.testdataRoot must not be empty")
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
	// 启动时只能比较已知的编译墙钟；测例墙钟（显式值或 cpuNs × clockRatio）由 flow 检查。
	// 这不是 sandbox 总耗时的上界：排队、回收和网络还会消耗调用期限。
	// 设小了的表现是「沙箱正常跑着，judge 自己先超时」，报出来是 SE，查半天查不到原因。
	if j.SandboxTimeout.Std() <= time.Duration(j.Compile.ClockNs) {
		return fmt.Errorf("judge.sandboxTimeout (%s) must be greater than judge.compile.clockNs (%s): otherwise judge times out first when a compile reaches its wall-clock limit, and the result is reported as a system error",
			j.SandboxTimeout, time.Duration(j.Compile.ClockNs))
	}
	return nil
}
