//go:build !linux || !amd64

package helper

import (
	"context"
	"fmt"
)

func Serve(context.Context, Config) error {
	return fmt.Errorf("the privileged helper only supports Linux amd64 in this version; falling back to the host is forbidden")
}
