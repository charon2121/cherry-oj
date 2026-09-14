package api

import (
	"cherry-oj/judge-engine/internal/sandbox/store"
	"errors"
	"io"
	"net/http"
)

func (s *Server) handleBlobPut(w http.ResponseWriter, r *http.Request) {
	body := http.MaxBytesReader(w, r.Body, s.opts.MaxBlobBytes)
	ref, err := s.store.Put(body)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ref": ref})
}

func (s *Server) handleBlobGet(w http.ResponseWriter, r *http.Request) {
	ref := r.PathValue("ref")
	rc, err := s.store.Get(ref)
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, err)
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err)
		return
	}
	// 即使客户端中途断开也释放 reader，否则 Store 仍会把该文件计入在用容量。
	defer rc.Close()

	w.Header().Set("Content-Type", "application/octet-stream")
	_, _ = io.Copy(w, rc)
}

func (s *Server) handleBlobDelete(w http.ResponseWriter, r *http.Request) {
	ref := r.PathValue("ref")
	if err := s.store.Delete(ref); err != nil {
		writeError(w, http.StatusInternalServerError, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ref": ref})
}
