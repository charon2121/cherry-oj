//go:build !linux

package helper

import "fmt"

func checkConfigPath(string) error { return fmt.Errorf("helper 配置仅可在 Linux 上加载") }
