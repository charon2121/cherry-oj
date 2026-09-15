//go:build !linux

package cgroup

import "fmt"

func openFilesystem(string) (filesystem, error) {
	return nil, fmt.Errorf("cgroup v2 后端只支持 Linux，不允许回退 host")
}
