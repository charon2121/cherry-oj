//go:build !linux || !amd64

package helper

import (
	"fmt"
	"os"
)

func isolatedNamespaces() uintptr { return 0 }
func (p *isolatedProcess) Start(executionGroup) error {
	return fmt.Errorf("隔离执行仅支持 Linux/amd64")
}
func validateWorkspace(*ownedFile) error          { return fmt.Errorf("隔离工作区仅支持 Linux/amd64") }
func (p *isolatedProcess) shutdownControl() error { return nil }
func (d workspaceDirectory) Open(string) (*os.File, int64, error) {
	return nil, 0, fmt.Errorf("隔离产物仅支持 Linux/amd64")
}
