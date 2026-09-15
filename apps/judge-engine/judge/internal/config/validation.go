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
		return fmt.Errorf("judge.httpAddr 不能为空")
	}
	if j.SandboxURL == "" {
		return fmt.Errorf("judge.sandboxURL 不能为空")
	}
	if j.SandboxTimeout <= 0 {
		return fmt.Errorf("judge.sandboxTimeout 必须为正，得到 %s", j.SandboxTimeout)
	}
	if j.EnvironmentFingerprint == "" {
		return fmt.Errorf("judge.environmentFingerprint 不能为空")
	}
	if j.TestdataRoot == "" {
		return fmt.Errorf("judge.testdataRoot 不能为空")
	}
	if j.ClockRatio <= 0 {
		return fmt.Errorf("judge.clockRatio 必须为正，得到 %d", j.ClockRatio)
	}
	if j.InlineThresholdBytes < 0 {
		return fmt.Errorf("judge.inlineThresholdBytes 不能为负，得到 %d", j.InlineThresholdBytes)
	}
	if j.OutputExcerptBytes < 0 {
		return fmt.Errorf("judge.outputExcerptBytes 不能为负，得到 %d", j.OutputExcerptBytes)
	}
	if j.MessageExcerptBytes < 0 {
		return fmt.Errorf("judge.messageExcerptBytes 不能为负，得到 %d", j.MessageExcerptBytes)
	}
	if j.Output.StdoutMaxBytes <= 0 {
		return fmt.Errorf("judge.output.stdoutMaxBytes 必须为正，得到 %d", j.Output.StdoutMaxBytes)
	}
	if j.Output.StderrMaxBytes <= 0 {
		return fmt.Errorf("judge.output.stderrMaxBytes 必须为正，得到 %d", j.Output.StderrMaxBytes)
	}
	if j.Compile.CPUNs <= 0 || j.Compile.MemoryBytes <= 0 || j.Compile.ClockNs <= 0 {
		return fmt.Errorf("judge.compile 的三项都必须为正，得到 %+v", j.Compile)
	}
	// 跨层预算：调用期限必须覆盖本节点配置的最长一次 /run。编译是配置层面最长的那一次；
	// 测试点的墙钟由请求给出（cpuNs × clockRatio），上界由节点硬界约束，不在这里。
	// 设小了的表现是「沙箱正常跑着，judge 自己先超时」，报出来是 SE，查半天查不到原因。
	if j.SandboxTimeout.Std() <= time.Duration(j.Compile.ClockNs) {
		return fmt.Errorf("judge.sandboxTimeout（%s）必须大于 judge.compile.clockNs（%s）："+
			"否则编译刚到墙钟上限，judge 这边已经先超时，结果被报成系统错误",
			j.SandboxTimeout, time.Duration(j.Compile.ClockNs))
	}
	return nil
}
