//go:build !linux

package helper

import "fmt"

func checkConfigPath(string) error {
	return fmt.Errorf("helper configuration can only be loaded on Linux")
}
