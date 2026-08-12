package api

import (
	"cherry-oj/judge-engine/internal/sandbox/store"
	"errors"
	"io"
	"net/http"
)

const maxBlobBytes = 64 << 20

func (s *Server) handleBlobPut(w http.ResponseWriter, r *http.Request) {
	// 限制 body 大小
	body := http.MaxBytesReader(w, r.Body, maxBlobBytes)
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
	defer rc.Close()

	w.Header().Set("Content-Type", "application/octet-stream")
	_, _ = io.Copy(w, rc) // 流式
}

func (s *Server) handleBlobDelete(w http.ResponseWriter, r *http.Request) {
	ref := r.PathValue("ref")
	if err := s.store.Delete(ref); err != nil {
		writeError(w, http.StatusInternalServerError, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ref": ref})
}
