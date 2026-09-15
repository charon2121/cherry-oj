// Package logging 提供 judge 与 sandbox 共用的结构化日志初始化。
package logging

import (
	"fmt"
	"io"
	"log/slog"
	"os"
	"strings"
	"time"
)

// New 创建同时写 stdout 与按 UTC 日期拆分文件的 JSON logger。
// 文件名固定为 <service>.YYYY-MM-DD.log，目录不可写时立即返回错误，避免服务启动后才丢日志。
func New(service, directory, level string) (*slog.Logger, io.Closer, error) {
	parsedLevel, err := parseLevel(level)
	if err != nil {
		return nil, nil, err
	}
	files, err := newDailyWriter(directory, service, time.Now)
	if err != nil {
		return nil, nil, err
	}
	logger := newJSONLogger(service, io.MultiWriter(os.Stdout, files), parsedLevel)
	return logger, files, nil
}

// Console 创建只写给定 writer 的 INFO JSON logger，用于配置尚未加载时的启动错误。
func Console(service string, output io.Writer) *slog.Logger {
	return newJSONLogger(service, output, slog.LevelInfo)
}

func newJSONLogger(service string, output io.Writer, level slog.Level) *slog.Logger {
	handler := slog.NewJSONHandler(output, &slog.HandlerOptions{
		Level: level,
		ReplaceAttr: func(groups []string, attr slog.Attr) slog.Attr {
			if len(groups) != 0 {
				return attr
			}
			switch attr.Key {
			case slog.TimeKey:
				attr.Key = "@timestamp"
				attr.Value = slog.StringValue(attr.Value.Time().UTC().Format(time.RFC3339Nano))
			case slog.MessageKey:
				attr.Key = "message"
			}
			return attr
		},
	})
	return slog.New(handler).With("service", service)
}

func parseLevel(level string) (slog.Level, error) {
	switch strings.ToUpper(level) {
	case "DEBUG":
		return slog.LevelDebug, nil
	case "INFO":
		return slog.LevelInfo, nil
	case "WARN":
		return slog.LevelWarn, nil
	case "ERROR":
		return slog.LevelError, nil
	default:
		return 0, fmt.Errorf("unsupported logging level %q", level)
	}
}
