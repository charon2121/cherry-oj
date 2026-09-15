package logging

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sync"
	"time"
)

var servicePattern = regexp.MustCompile(`^[a-z][a-z0-9-]{0,63}$`)

type dailyWriter struct {
	mu        sync.Mutex
	directory string
	service   string
	now       func() time.Time
	date      string
	file      *os.File
}

func newDailyWriter(directory, service string, now func() time.Time) (*dailyWriter, error) {
	if directory == "" {
		return nil, fmt.Errorf("logging directory is empty")
	}
	if !servicePattern.MatchString(service) {
		return nil, fmt.Errorf("invalid logging service name %q", service)
	}
	if err := os.MkdirAll(directory, 0o755); err != nil {
		return nil, fmt.Errorf("create logging directory %s: %w", directory, err)
	}
	w := &dailyWriter{directory: directory, service: service, now: now}
	if err := w.rotateLocked(); err != nil {
		return nil, err
	}
	return w, nil
}

func (w *dailyWriter) Write(p []byte) (int, error) {
	w.mu.Lock()
	defer w.mu.Unlock()
	if current := w.currentDate(); current != w.date {
		if err := w.rotateLocked(); err != nil {
			return 0, err
		}
	}
	return w.file.Write(p)
}

func (w *dailyWriter) Close() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.file == nil {
		return nil
	}
	err := w.file.Close()
	w.file = nil
	return err
}

func (w *dailyWriter) rotateLocked() error {
	date := w.currentDate()
	path := filepath.Join(w.directory, w.service+"."+date+".log")
	next, err := os.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o644)
	if err != nil {
		return fmt.Errorf("open daily log file %s: %w", path, err)
	}
	if w.file != nil {
		if err := w.file.Close(); err != nil {
			_ = next.Close()
			return fmt.Errorf("close previous daily log file: %w", err)
		}
	}
	w.file = next
	w.date = date
	return nil
}

func (w *dailyWriter) currentDate() string {
	return w.now().UTC().Format(time.DateOnly)
}
