package helper

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"runtime/debug"
	"sync"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
	"cherry-oj/judge-engine/internal/sandbox/cgroup"
)

const installationProbeTimeout = 5 * time.Second

// checkInstallation 只读校验安装可信性；成功不代表已经通过真实隔离启动。
func checkInstallation(c Config) (string, error) {
	if err := c.Validate(); err != nil {
		return "", err
	}
	info, ok := debug.ReadBuildInfo()
	pure := false
	if ok {
		for _, setting := range info.Settings {
			if setting.Key == "CGO_ENABLED" && setting.Value == "0" {
				pure = true
			}
		}
	}
	if !pure {
		return "", fmt.Errorf("helper 必须使用 CGO_ENABLED=0 构建")
	}
	if os.Geteuid() != 0 {
		return "", fmt.Errorf("helper 必须由 root 托管")
	}
	for _, p := range []string{c.StateDir, c.JobsDir} {
		if err := securePath(p, true); err != nil {
			return "", err
		}
	}
	if err := verifyRoot(c); err != nil {
		return "", err
	}
	executable, err := os.Executable()
	if err != nil {
		return "", err
	}
	if err = securePath(executable, false); err != nil {
		return "", err
	}
	binaryInfo, err := os.Stat(executable)
	if err != nil {
		return "", err
	}
	if !binaryInfo.Mode().IsRegular() || binaryInfo.Mode()&(os.ModeSetuid|os.ModeSetgid) != 0 {
		return "", fmt.Errorf("helper 必须为不带 setuid/setgid 的普通可执行文件")
	}

	return executable, nil
}

// probeInstallation 在监听前通过真实执行链验证部署；不使用宿主 true。
func probeInstallation(ctx context.Context, c Config, manager *cgroup.Manager, executable string) error {
	probeCtx, probeCancel := context.WithTimeout(ctx, installationProbeTimeout)
	defer probeCancel()
	probeR, probeW := io.Pipe()
	closeInput := sync.OnceValue(probeR.Close)
	probeInputCancel := func() { probeCancel(); closeInput() }
	// 探测用固定限额走同一执行链，避免部署可用性被客户端请求默认值影响。
	probe := hostexec.Request{Version: hostexec.Version, Command: []string{"true"}, Limits: contract.ExplicitLimits(contract.Limits{CPUNs: int64(2 * time.Second), ClockNs: int64(5 * time.Second), MemoryBytes: 128 << 20, MaxProcesses: 64, StdoutMaxBytes: 1024, StderrMaxBytes: 1024})}
	run := newExecution(probe, executionOptions{
		config: c, source: probeR, executable: executable, cancelInput: probeInputCancel,
		groups: func(l cgroup.Limits) (executionGroup, error) { return manager.New(l) },
	})
	facts, err := run.Run(probeCtx)
	probeInputCancel()
	if err = errors.Join(err, closeInput(), probeW.Close(), facts.Close()); err != nil {
		return err
	}
	if facts.Reason != "" || facts.ExitCode != 0 || facts.Signal != 0 || facts.Usage.CPUNs <= 0 || facts.Usage.MemoryBytes <= 0 || len(facts.Stdout) != 0 || len(facts.Stderr) != 0 {
		return fmt.Errorf("隔离启动能力冒烟失败: reason=%s error=%s stderr=%q", facts.Reason, facts.Error, facts.Stderr)
	}
	return nil
}
