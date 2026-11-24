// problem-build 为 C++ ACM 题生成并验证可上传的测试数据。
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"os/signal"
	"syscall"
	"time"
)

const buildTimeout = 10 * time.Minute

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	code := execute(ctx, os.Args[1:], os.Stdout, os.Stderr)
	stop()
	os.Exit(code)
}

func execute(ctx context.Context, args []string, stdout, stderr io.Writer) int {
	options, err := parseOptions(args, stderr)
	if errors.Is(err, flag.ErrHelp) {
		return 0
	}
	if err != nil {
		fmt.Fprintln(stderr, err)
		return 1
	}
	input, err := loadBuild(options)
	if err != nil {
		fmt.Fprintln(stderr, err)
		return 1
	}
	if err = os.Mkdir(options.output, 0o700); err != nil {
		fmt.Fprintf(stderr, "建立新的产物目录 %s：%v\n", options.output, err)
		return 1
	}

	ctx, cancel := context.WithTimeout(ctx, buildTimeout)
	defer cancel()
	judge := newJudgeClient(options.judgeURL)
	defer judge.client.CloseIdleConnections()
	result := buildReport{input: input}
	err = build(ctx, judge, &result, options.output)
	if err != nil {
		result.failure = err.Error()
	}
	if writeErr := writeReport(options.output, result); writeErr != nil {
		err = errors.Join(err, writeErr, removeArchive(options.output))
	}
	if err != nil {
		fmt.Fprintf(stderr, "验题失败：%v\n报告目录：%s\n", err, options.output)
		return 1
	}
	fmt.Fprintf(stdout, "验题通过，数据指纹 %s\n产物目录：%s\n", result.digest, options.output)
	return 0
}
