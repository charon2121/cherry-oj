package config

import (
	"time"

	"cherry-oj/judge-engine/execution"
	"cherry-oj/judge-engine/execution/backend"

	platform "cherry-oj/judge-engine/internal/platform/config"
)

// MaxClockNs 是执行层能接受的单次执行墙钟硬界（与 C 执行器一致）。判题编排不能直接引用执行层，
// 由配置包转述给它：请求推出的墙钟超过它，执行器必然拒绝，不如在上传源码前就说清楚。
const MaxClockNs = backend.MaxClockNs

// Default 返回 judge 的有界默认配置。
//
// 有默认值意味着**配置文件可以缺失**，也意味着 YAML 里只需要写要改的那几项——
// 这是「零值陷阱」的解药：不填不等于填 0。
func Default() Config {
	return Config{
		Logging: platform.Logging{
			Path:  "./logs",
			Level: "INFO",
		},
		Judge: Settings{
			Node:                 defaultNode(),
			HTTPAddr:             "127.0.0.1:5051",
			TestdataRoot:         "/srv/cherry-oj/testdata",
			StrictWhitespace:     false, // 默认宽松：一个换行不该卡住新手
			RevealExpected:       false, // ★ 默认不泄题；教学部署自己打开
			ClockRatio:           10,
			InlineThresholdBytes: 256 << 10,
			OutputExcerptBytes:   4 << 10,
			MessageExcerptBytes:  8 << 10,
			Output: Output{
				StdoutMaxBytes: 1 << 20,
				StderrMaxBytes: 1 << 20,
			},
			Compile: Compile{
				CPUNs:       int64(10 * time.Second),
				MemoryBytes: 1 << 30, // 1 GiB
				ClockNs:     int64(20 * time.Second),
			},
		},
		Execution: execution.DefaultEngineSettings(),
	}
}
