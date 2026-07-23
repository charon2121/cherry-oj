package container

import (
	"cherry-oj/judge-engine/internal/contract"
	"context"
	"io"
	"io/fs"
)

type Container interface {
	Start(ctx context.Context, s Spec) (Process, error)
	PutFile(name string, r io.Reader, mode fs.FileMode) error
	GetFile(name string) (io.ReadCloser, error)
	Reset() error
	Close() error
}

type Spec struct {
	Command        []string
	Env            []string
	Stdin          io.Reader
	Stdout, Stderr io.Writer
	Limits         contract.Limits
}

type Process interface {
	Wait(ctx context.Context) (Usage, error)
}

type Usage struct {
	ExitCode    int
	Signal      int
	CPUNs       int64
	MemoryBytes int64
}
