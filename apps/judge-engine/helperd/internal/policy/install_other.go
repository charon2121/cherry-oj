//go:build !linux || !amd64

package policy

import "fmt"

func Install(Profile) error {
	return fmt.Errorf("seccomp in this version only implements the Linux/amd64 native ABI")
}
