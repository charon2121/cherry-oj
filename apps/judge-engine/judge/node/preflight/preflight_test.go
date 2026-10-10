package preflight_test

import (
	"context"
	"testing"

	"cherry-oj/judge-engine/execution"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/node/preflight"
)

type fixedVersion struct{ v contract.SandboxVersion }

func (f fixedVersion) Version(context.Context) (contract.SandboxVersion, error) { return f.v, nil }

// linux 隔离与部署清单必须同时成立：只有一边成立说明部署与配置不一致，两种都要拒绝。
func TestCheckRefusesMissingManifestOrWrongBackend(t *testing.T) {
	for _, isolation := range []string{"linux", "devhost"} {
		t.Run(isolation, func(t *testing.T) {
			cfg := config.Default().Judge
			if isolation == "devhost" {
				cfg.Node.DeploymentManifest = "/opt/cherry-oj/etc/deployment.json"
			}
			v := fixedVersion{contract.SandboxVersion{Name: execution.Name, Version: execution.Version, Isolation: isolation}}
			if err := preflight.Check(context.Background(), cfg, v); err == nil {
				t.Fatal("invalid deployment accepted")
			}
		})
	}
}

// 自检消费的是进程内执行层已有的能力，不另建一个探测通道。
var _ preflight.Sandbox = (*execution.Engine)(nil)
