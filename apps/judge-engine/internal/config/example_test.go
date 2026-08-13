package config

import "testing"

// 示例配置文件必须能被加载且合法 —— 否则它只是篇文档，不是可用的配置
func TestExampleConfigLoads(t *testing.T) {
	cfg, err := Load("../../config.example.yaml")
	if err != nil {
		t.Fatalf("示例配置加载失败: %v", err)
	}
	if cfg.Judge.StrictWhitespace {
		t.Errorf("示例配置的 strictWhitespace 应为 false")
	}
	if cfg.Judge.ClockRatio != 10 {
		t.Errorf("clockRatio=%d", cfg.Judge.ClockRatio)
	}
}
