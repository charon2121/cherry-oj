package config

import "fmt"

// Validate 把「配错了」挡在启动时，而不是让它伪装成一个合理的判题结论。
//
// 这条教训在这个项目里反复出现：limits 的 cpuNs=0 会让每道题秒 TLE，
// 而返回的结果看起来完全正常。配置也一样——宁可起不来，也别悄悄跑错。
func (c Config) Validate() error {
	if c.Logging.Path == "" {
		return fmt.Errorf("logging.path 不能为空")
	}
	switch c.Logging.Level {
	case "DEBUG", "INFO", "WARN", "ERROR":
	default:
		return fmt.Errorf("logging.level 必须是 DEBUG、INFO、WARN 或 ERROR，得到 %q", c.Logging.Level)
	}

	if c.Sandbox.HTTPAddr == "" {
		return fmt.Errorf("sandbox.httpAddr 不能为空")
	}
	if c.Sandbox.Parallelism <= 0 || c.Sandbox.Parallelism > 256 {
		return fmt.Errorf("sandbox.parallelism 必须为1～256，得到 %d", c.Sandbox.Parallelism)
	}
	if c.Sandbox.Store.MaxBlobBytes <= 0 || c.Sandbox.Store.MaxBlobBytes > 64<<20 {
		return fmt.Errorf("sandbox.store.maxBlobBytes 必须为1～64MiB，得到 %d", c.Sandbox.Store.MaxBlobBytes)
	}

	if c.Sandbox.Backend != "linux" && c.Sandbox.Backend != "trusted-host" {
		return fmt.Errorf("sandbox.backend必须为linux或trusted-host")
	}
	if c.Sandbox.Backend == "linux" && (c.Sandbox.HelperSocket == "" || c.Sandbox.WorkspaceRoot == "" || c.Sandbox.Store.Root == "") {
		return fmt.Errorf("linux后端需要helperSocket、workspaceRoot和store.root")
	}
	if c.Sandbox.QueueSize <= 0 || c.Sandbox.QueueSize > 1024 || c.Sandbox.MaxRequestBytes <= 0 || c.Sandbox.MaxRequestBytes > 8<<20 {
		return fmt.Errorf("sandbox排队或请求体上限无效")
	}
	if c.Sandbox.Store.MaxTotalBytes < c.Sandbox.Store.MaxBlobBytes || c.Sandbox.Store.MaxEntries <= 0 || c.Sandbox.Store.Retention <= 0 {
		return fmt.Errorf("sandbox.store总量/条目/保留期无效")
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
	return nil
}
