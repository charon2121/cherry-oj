package store

import (
	"io"
)

type Store interface {
	Put(r io.Reader) (ref string, err error)
	Open(ref string) (io.ReadCloser, error)
	Delete(ref string) error
}
