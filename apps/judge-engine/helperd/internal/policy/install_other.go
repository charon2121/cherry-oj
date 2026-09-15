//go:build !linux || !amd64

package policy

import "fmt"

func Install(Profile) error { return fmt.Errorf("seccomp 首版仅实现 Linux/amd64 原生 ABI") }
