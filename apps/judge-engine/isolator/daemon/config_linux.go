//go:build linux && amd64

package daemon

func checkConfigPath(path string) error { return securePath(path, false) }
