package config

import "time"

// Default 返回有界默认配置；Linux隔离服务仍需先准备helper和权限。
//
// 有默认值意味着**配置文件可以缺失**，也意味着 YAML 里只需要写要改的那几项——
// 这是「零值陷阱」的解药：不填不等于填 0。
func Default() Config {
	return Config{
		Logging: LoggingConfig{
			Path:  "./logs",
			Level: "INFO",
		},
		Sandbox: SandboxConfig{
			HTTPAddr:        "127.0.0.1:5050",
			Parallelism:     1,
			Backend:         "linux",
			HelperSocket:    "/run/cherry-sandbox/helper.sock",
			WorkspaceRoot:   "./data/sandbox-work",
			QueueSize:       8,
			MaxRequestBytes: 2 << 20,
			Store: StoreConfig{
				Root:          "./data/sandbox-blobs",
				MaxBlobBytes:  64 << 20,
				MaxTotalBytes: 512 << 20,
				MaxEntries:    4096,
				Retention:     Duration(time.Hour),
			},
		},
		Judge: JudgeConfig{
			Node:                   defaultNode(),
			HTTPAddr:               "127.0.0.1:5051",
			SandboxURL:             "http://127.0.0.1:5050",
			SandboxTimeout:         Duration(60 * time.Second),
			EnvironmentFingerprint: "local-development",
			TestdataRoot:           "/srv/cherry-oj/testdata",
			StrictWhitespace:       false, // 默认宽松：一个换行不该卡住新手
			RevealExpected:         false, // ★ 默认不泄题；教学部署自己打开
			ClockRatio:             10,
			InlineThresholdBytes:   256 << 10,
			OutputExcerptBytes:     4 << 10,
			MessageExcerptBytes:    8 << 10,
			Output: OutputConfig{
				StdoutMaxBytes: 1 << 20,
				StderrMaxBytes: 1 << 20,
			},
			Compile: CompileConfig{
				CPUNs:       int64(10 * time.Second),
				MemoryBytes: 1 << 30, // 1 GiB
				ClockNs:     int64(20 * time.Second),
			},
		},
	}
}
