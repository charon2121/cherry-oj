//go:build linux && amd64

package helper

func checkConfigPath(path string) error { return securePath(path, false) }
