//go:build linux && amd64

// sandbox-helper 只能由本机服务管理器启动；配置由 root 管理，不接受远端特权参数。
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"cherry-oj/judge-engine/isolator/daemon"
	"cherry-oj/judge-engine/isolator/execstage"
	"cherry-oj/judge-engine/isolator/initproc"
	"cherry-oj/judge-engine/isolator/startup"
)

func main() {
	// 同一个二进制扮演三个进程角色：常驻 daemon（P3）、namespace 内的 init（P4）、
	// 执行用户命令前的 exec（P5）。P4/P5 只消费继承的 FD，必须在读取配置、监听信号
	// 或建立任何 goroutine 之前分流，且永不返回，避免误入特权服务循环。
	if len(os.Args) == 2 {
		switch os.Args[1] {
		case startup.InitArg:
			initproc.Run()
		case startup.ExecArg:
			execstage.Run()
		}
	}
	path := flag.String("config", "", "root 管理的 helper JSON 配置绝对路径")
	flag.Parse()
	if *path == "" {
		fmt.Fprintln(os.Stderr, "--config is required")
		os.Exit(2)
	}
	c, err := daemon.LoadConfig(*path)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()
	if err = daemon.Serve(ctx, c); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
