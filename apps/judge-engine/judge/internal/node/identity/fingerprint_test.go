package identity

import (
	"testing"

	"cherry-oj/judge-engine/judge/internal/config"
)

func TestEnvironmentFingerprintChangesWithRuntimeButNotNodeIdentity(t *testing.T) {
	c := config.Default().Judge
	c.Node.Enabled, c.Node.ControlToken = true, "control"
	env := Environment{Architecture: "amd64", CPUModel: "base CPU", OSVersion: "base os",
		KernelVersion: "base kernel", SandboxVersion: "base sandbox",
		ToolchainVersion: "base compiler", RuntimeDigest: "base quotas"}
	fingerprint := func(c config.Settings, env Environment) string {
		t.Helper()
		id, e := New(c, env)
		if e != nil {
			t.Fatal(e)
		}
		return id.Registration().EnvironmentFingerprint
	}
	original := fingerprint(c, env)

	// 节点位置属于「这台机器是谁」，不属于「这是什么环境」：同一环境的两个节点必须同指纹。
	c.Node.ID = "another-node"
	c.Node.AdvertiseURL = "http://127.0.0.1:9999"
	if fingerprint(c, env) != original {
		t.Fatal("identity changed compatibility group")
	}

	// 执行环境的事实变了，指纹必须变。
	for name, change := range map[string]func(*Environment){
		"CPU":     func(e *Environment) { e.CPUModel = "other CPU" },
		"内核":      func(e *Environment) { e.KernelVersion = "other kernel" },
		"工具链":     func(e *Environment) { e.ToolchainVersion = "other compiler" },
		"运行时配额":   func(e *Environment) { e.RuntimeDigest = "other quotas" },
		"架构":      func(e *Environment) { e.Architecture = "arm64" },
		"sandbox": func(e *Environment) { e.SandboxVersion = "other sandbox" },
	} {
		changed := env
		change(&changed)
		if fingerprint(c, changed) == original {
			t.Fatalf("%s 变化后仍复用了指纹", name)
		}
	}

	// 判题策略变了，指纹同样必须变——同一份提交可能因此得到不同结论。
	strict := c
	strict.StrictWhitespace = !strict.StrictWhitespace
	if fingerprint(strict, env) == original {
		t.Fatal("空白严格度变化后仍复用了指纹")
	}
}
