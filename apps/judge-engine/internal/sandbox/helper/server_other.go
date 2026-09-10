//go:build !linux || !amd64

package helper

import (
	"context"
	"fmt"
)

func Serve(context.Context, Config) error {
	return fmt.Errorf("特权 helper 首版仅支持 Linux amd64，禁止回退 host")
}
