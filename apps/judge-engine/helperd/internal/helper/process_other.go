//go:build !linux || !amd64

package helper

import (
	"fmt"
	"os"
)

func isolatedNamespaces() uintptr { return 0 }
func (p *isolatedProcess) Start(executionGroup) error {
	return fmt.Errorf("isolated execution is only supported on Linux/amd64")
}
func validateWorkspace(*ownedFile) error {
	return fmt.Errorf("isolated workspaces are only supported on Linux/amd64")
}
func (p *isolatedProcess) shutdownControl() error { return nil }
func (d workspaceDirectory) Open(string) (*os.File, int64, error) {
	return nil, 0, fmt.Errorf("isolated artifacts are only supported on Linux/amd64")
}
