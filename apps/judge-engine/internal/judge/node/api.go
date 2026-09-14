package node

import (
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"time"

	"cherry-oj/judge-engine/internal/contract"
)

const (
	multipartOverheadBytes  = 2 << 20
	maxInstallMetadataBytes = 1 << 20
	installReadTimeout      = 5 * time.Minute
)

func (n *Node) Handler(fallback http.Handler) http.Handler {
	mux := http.NewServeMux()
	mux.Handle("/", fallback)
	mux.HandleFunc("POST /internal/judge-node/v1/install", n.handleInstall)
	return mux
}
func (n *Node) handleInstall(w http.ResponseWriter, r *http.Request) {
	if subtle.ConstantTimeCompare([]byte(r.Header.Get("Authorization")), []byte("Bearer "+n.cfg.ControlToken)) != 1 {
		nodeError(w, 401, "NODE_UNAUTHORIZED")
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, n.cfg.MaxArchiveBytes+multipartOverheadBytes)
	// 防止客户端无限慢传占用一个安装槽位。
	_ = http.NewResponseController(w).SetReadDeadline(time.Now().Add(installReadTimeout))
	reader, err := r.MultipartReader()
	if err != nil {
		nodeError(w, 400, "NODE_INVALID_REQUEST")
		return
	}
	part, err := reader.NextPart()
	if err != nil || part.FormName() != "metadata" {
		nodeError(w, 400, "NODE_INVALID_REQUEST")
		return
	}
	var m contract.NodeInstall
	err = decodeJSON(io.LimitReader(part, maxInstallMetadataBytes+1), &m)
	part.Close()
	if err != nil {
		nodeError(w, 400, "NODE_INVALID_REQUEST")
		return
	}
	part, err = reader.NextPart()
	if err != nil || part.FormName() != "archive" {
		nodeError(w, 400, "NODE_INVALID_REQUEST")
		return
	}
	n.logger.Info("judge.node.install.started", "nodeId", n.registration.NodeID)
	receipt, err := n.Install(r.Context(), m, &lastPart{part: part, multipart: reader})
	if err != nil {
		n.logger.Warn("judge.node.install.failed", "nodeId", n.registration.NodeID)
		code := "NODE_DATA_REJECTED"
		status := 422
		if err == errConflict {
			code = "NODE_DATA_CONFLICT"
			status = 409
		}
		nodeError(w, status, code)
		return
	}
	n.logger.Info("judge.node.install.ready", "nodeId", receipt.NodeID, "testDataVersionId", receipt.TestDataVersionID)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(receipt)
}
func nodeError(w http.ResponseWriter, status int, code string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]string{"code": code, "detail": "Node request could not be completed"})
}
func decodeJSON(r io.Reader, value any) error {
	d := json.NewDecoder(r)
	d.DisallowUnknownFields()
	if err := d.Decode(value); err != nil {
		return err
	}
	var extra any
	if err := d.Decode(&extra); err != io.EOF {
		return fmt.Errorf("trailing JSON")
	}
	return nil
}

// EOF 必须同时证明 archive 是最后一个 part，才能进入原子提交。
type lastPart struct {
	part      *multipart.Part
	multipart *multipart.Reader
}

func (r *lastPart) Read(b []byte) (int, error) {
	count, err := r.part.Read(b)
	if err == io.EOF {
		if _, e := r.multipart.NextPart(); e != io.EOF {
			return count, errRejected
		}
	}
	return count, err
}
