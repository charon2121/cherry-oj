//go:build !linux

package cgroup

import "fmt"

func openFilesystem(string) (filesystem, error) {
	return nil, fmt.Errorf("the cgroup v2 backend only supports Linux; falling back to the host is not allowed")
}
