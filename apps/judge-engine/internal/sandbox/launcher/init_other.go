//go:build !linux || !amd64

package launcher

import "os"

func Dispatch() bool {
	if len(os.Args) == 2 && (os.Args[1] == "--isolated-init" || os.Args[1] == "--isolated-exec") {
		os.Exit(125)
	}
	return false
}
