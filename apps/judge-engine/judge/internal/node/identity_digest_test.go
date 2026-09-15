package node

import (
	"testing"

	"cherry-oj/judge-engine/judge/internal/config"
)

// 固定输入必须给出固定摘要。这个基准值把「哪些字段进入环境身份」钉死：
// 改动 policyFingerprint 的字段、JSON 名或取值都会让本测试失败，于是轮换全网节点身份
// 从「改错一个字段的副作用」变成「必须动手改基准值的显式决定」。
//
// 真要改：先确认这次变化确实应当让同一份提交得到不同判题结论，再更新下面的值，
// 并按节点协议安排一次环境切换（新指纹只会以 REGISTERED 出现，不会静默替换 ACTIVE）。
const pinnedConfigDigest = "4dd1c3e0ed0a7e1fcdb09489622cb5a8aaa40cc0e37cff112d7fc596c5689e6c"

func samplePolicy() policyFingerprint {
	return policyFingerprint{
		StrictWhitespace: true, RevealExpected: false, ClockRatio: 10,
		OutputExcerptBytes: 4096, MessageExcerptBytes: 8192,
		StdoutMaxBytes: 1 << 20, StderrMaxBytes: 1 << 20,
		CompileCPUNs: 10_000_000_000, CompileMemoryBytes: 1 << 30, CompileClockNs: 20_000_000_000,
		RuntimeDigest: "sha256:example/judge/sha256:example",
	}
}

func TestConfigDigestIsPinned(t *testing.T) {
	got, err := configDigest(samplePolicy())
	if err != nil {
		t.Fatal(err)
	}
	if got != pinnedConfigDigest {
		t.Fatalf("配置摘要已变化：\n  实际 %s\n  基准 %s\n"+
			"若这次改动确实应当改变环境身份，请更新基准值并安排一次环境切换；"+
			"若不应当，说明有字段被误加进了 policyFingerprint。", got, pinnedConfigDigest)
	}
}

// 与判题结论无关的配置项不得进入身份：改它们不该轮换全网节点。
// 这正是重构前的缺陷——序列化整个配置结构，加任何字段都会静默改变指纹。
func TestUnrelatedSettingsDoNotAffectDigest(t *testing.T) {
	base := config.Default().Judge
	want, err := configDigest(policyOf(base, "fixed"))
	if err != nil {
		t.Fatal(err)
	}
	for name, mutate := range map[string]func(*config.Settings){
		"inlineThresholdBytes": func(s *config.Settings) { s.InlineThresholdBytes = 1 },
		"sandboxTimeout":       func(s *config.Settings) { s.SandboxTimeout *= 2 },
		"sandboxURL":           func(s *config.Settings) { s.SandboxURL = "http://elsewhere:5050" },
		"httpAddr":             func(s *config.Settings) { s.HTTPAddr = "0.0.0.0:1" },
		"testdataRoot":         func(s *config.Settings) { s.TestdataRoot = "/elsewhere" },
		"节点位置":                 func(s *config.Settings) { s.Node.ID, s.Node.AdvertiseURL = "n2", "http://h:1" },
	} {
		changed := base
		mutate(&changed)
		got, err := configDigest(policyOf(changed, "fixed"))
		if err != nil {
			t.Fatal(err)
		}
		if got != want {
			t.Errorf("%s 与判题结论无关，却改变了配置摘要", name)
		}
	}
}

// 会改变判题结论的配置项必须进入身份。
func TestJudgingPolicyAffectsDigest(t *testing.T) {
	base := config.Default().Judge
	want, err := configDigest(policyOf(base, "fixed"))
	if err != nil {
		t.Fatal(err)
	}
	for name, mutate := range map[string]func(*config.Settings){
		"strictWhitespace": func(s *config.Settings) { s.StrictWhitespace = !s.StrictWhitespace },
		"revealExpected":   func(s *config.Settings) { s.RevealExpected = !s.RevealExpected },
		"clockRatio":       func(s *config.Settings) { s.ClockRatio++ },
		"stdoutMaxBytes":   func(s *config.Settings) { s.Output.StdoutMaxBytes++ },
		"stderrMaxBytes":   func(s *config.Settings) { s.Output.StderrMaxBytes++ },
		"compile.cpuNs":    func(s *config.Settings) { s.Compile.CPUNs++ },
		"compile.memory":   func(s *config.Settings) { s.Compile.MemoryBytes++ },
		"compile.clockNs":  func(s *config.Settings) { s.Compile.ClockNs++ },
		"输出截断长度":           func(s *config.Settings) { s.OutputExcerptBytes++ },
		"消息截断长度":           func(s *config.Settings) { s.MessageExcerptBytes++ },
	} {
		changed := base
		mutate(&changed)
		got, err := configDigest(policyOf(changed, "fixed"))
		if err != nil {
			t.Fatal(err)
		}
		if got == want {
			t.Errorf("%s 会改变判题结论，却没有改变配置摘要", name)
		}
	}
	// 运行时摘要覆盖二进制与资源配额，同样必须参与。
	other, err := configDigest(policyOf(base, "other"))
	if err != nil {
		t.Fatal(err)
	}
	if other == want {
		t.Error("运行时摘要没有参与配置摘要")
	}
}
