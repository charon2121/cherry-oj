// Package node 管理 Judge 自注册与节点私有数据安装，不涉及 sandbox 执行语义。
package node

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"syscall"
	"time"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/judge/language"
)

var errIdentityConflict = errors.New("node identity conflict")

type Node struct {
	cfg          config.NodeConfig
	root         string
	registration contract.NodeRegistration
	client       *http.Client
	logger       *slog.Logger
	lock         *os.File
	installMu    sync.Mutex
}

func New(j config.JudgeConfig, logger *slog.Logger) (*Node, error) {
	if err := j.Node.Validate(); err != nil {
		return nil, err
	}
	if logger == nil {
		logger = slog.Default()
	}
	root, err := filepath.Abs(j.TestdataRoot)
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(root, 0700); err != nil {
		return nil, err
	}
	info, err := os.Lstat(root)
	if err != nil || info.Mode()&os.ModeSymlink != 0 {
		return nil, fmt.Errorf("node data root must not traverse symlinks")
	}
	root, err = filepath.EvalSymlinks(root)
	if err != nil {
		return nil, err
	}
	lock, err := os.OpenFile(filepath.Join(root, ".node.lock"), os.O_CREATE|os.O_RDWR|syscall.O_NOFOLLOW, 0600)
	if err != nil {
		return nil, err
	}
	if err := syscall.Flock(int(lock.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil {
		lock.Close()
		return nil, fmt.Errorf("data root is already owned by a node: %w", err)
	}
	session := make([]byte, 16)
	if _, err := rand.Read(session); err != nil {
		lock.Close()
		return nil, err
	}
	session[6] = (session[6] & 15) | 64
	session[8] = (session[8] & 63) | 128
	id := fmt.Sprintf("%x-%x-%x-%x-%x", session[0:4], session[4:6], session[6:8], session[8:10], session[10:16])
	policy := j
	policy.Node = config.NodeConfig{}
	policy.HTTPAddr = ""
	policy.SandboxURL = ""
	policy.TestdataRoot = ""
	policy.EnvironmentFingerprint = ""
	binaryDigest, err := executableDigest()
	if err != nil {
		lock.Close()
		return nil, err
	}
	policy.Node.RuntimeDigest = j.Node.RuntimeDigest + "/judge/" + binaryDigest
	encoded, err := json.Marshal(policy)
	if err != nil {
		lock.Close()
		return nil, err
	}
	digest := sha256.Sum256(encoded)
	cpp, _ := language.Get("cpp")
	langJSON, err := json.Marshal(cpp)
	if err != nil {
		lock.Close()
		return nil, err
	}
	langDigest := sha256.Sum256(langJSON)
	architecture := j.Node.Architecture
	if architecture == "" {
		architecture = runtime.GOARCH
	}
	registration := contract.NodeRegistration{NodeID: j.Node.ID, EnvironmentFingerprint: "", SessionID: id, Endpoint: strings.TrimRight(j.Node.AdvertiseURL, "/"), Architecture: architecture, CPUModel: j.Node.CPUModel, OSVersion: j.Node.OSVersion, KernelVersion: j.Node.KernelVersion, JudgeVersion: "0.1.0-mvp", SandboxVersion: j.Node.SandboxVersion, ConfigDigest: hex.EncodeToString(digest[:]), Languages: []contract.NodeLanguage{{LanguageID: "cpp", ToolchainVersion: j.Node.ToolchainVersion, LanguageConfigDigest: hex.EncodeToString(langDigest[:])}}}
	identity := registration
	identity.NodeID, identity.SessionID, identity.Endpoint = "", "", ""
	fingerprintJSON, err := json.Marshal(identity)
	if err != nil {
		lock.Close()
		return nil, err
	}
	fingerprint := sha256.Sum256(fingerprintJSON)
	registration.EnvironmentFingerprint = hex.EncodeToString(fingerprint[:])
	return &Node{cfg: j.Node, root: root, registration: registration, logger: logger, lock: lock, client: &http.Client{Timeout: j.Node.RequestTimeout.Std(), CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}}, nil
}
func (n *Node) Close() error { return n.lock.Close() }
func (n *Node) Registration() contract.NodeRegistration {
	r := n.registration
	r.Languages = append([]contract.NodeLanguage(nil), r.Languages...)
	return r
}

// Run 随进程 context 退出；控制面不可用只延迟注册，不关闭 Judge 健康入口。
func (n *Node) Run(ctx context.Context) {
	registered := false
	backoff := time.Second
	for ctx.Err() == nil {
		var payload any = n.registration
		route := "register"
		if registered {
			route = "heartbeat"
			payload = contract.NodeHeartbeat{NodeID: n.registration.NodeID, EnvironmentFingerprint: n.registration.EnvironmentFingerprint, SessionID: n.registration.SessionID}
		}
		lease, err := n.exchange(ctx, route, payload)
		delay := n.cfg.HeartbeatInterval.Std()
		if errors.Is(err, errIdentityConflict) {
			n.logger.Error("judge.node.identity.conflict", "nodeId", n.registration.NodeID)
			return
		}
		if err != nil {
			n.logger.Warn("judge.node.control.failed", "nodeId", n.registration.NodeID, "operation", route)
			registered = false
			delay = backoff
			backoff = min(backoff*2, 30*time.Second)
		} else {
			if !registered {
				n.logger.Info("judge.node.registered", "nodeId", lease.NodeID, "environmentId", lease.EnvironmentID)
			}
			registered = true
			backoff = time.Second
			delay = min(delay, time.Duration(lease.LeaseDurationNs)/3)
		}
		timer := time.NewTimer(delay)
		select {
		case <-ctx.Done():
			timer.Stop()
			return
		case <-timer.C:
		}
	}
}
func (n *Node) exchange(ctx context.Context, route string, payload any) (contract.NodeLease, error) {
	var lease contract.NodeLease
	b, err := json.Marshal(payload)
	if err != nil {
		return lease, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, strings.TrimRight(n.cfg.ControlPlaneURL, "/")+"/internal/judge-nodes/v1/"+route, bytes.NewReader(b))
	if err != nil {
		return lease, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+n.cfg.ControlToken)
	response, err := n.client.Do(req)
	if err != nil {
		return lease, err
	}
	defer response.Body.Close()
	if response.StatusCode == http.StatusConflict {
		return lease, errIdentityConflict
	}
	if response.StatusCode != 200 {
		return lease, fmt.Errorf("node control status %d", response.StatusCode)
	}
	if err := decodeJSON(io.LimitReader(response.Body, 4097), &lease); err != nil {
		return lease, err
	}
	if lease.NodeID != n.registration.NodeID || !uuidPattern.MatchString(lease.EnvironmentID) || lease.LeaseDurationNs < 3_000_000 || lease.LeaseDurationNs > 300_000_000_000 {
		return lease, fmt.Errorf("invalid node lease")
	}
	return lease, nil
}
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
	r.Body = http.MaxBytesReader(w, r.Body, n.cfg.MaxArchiveBytes+(2<<20))
	// 防止客户端无限慢传占用一个安装槽位。
	_ = http.NewResponseController(w).SetReadDeadline(time.Now().Add(5 * time.Minute))
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
	err = decodeJSON(io.LimitReader(part, (1<<20)+1), &m)
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

// Read the running executable as a bounded stream: code changes invalidate the
// compatibility group even before the human-readable release label changes.
func executableDigest() (string, error) {
	path, err := os.Executable()
	if err != nil {
		return "", err
	}
	file, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer file.Close()
	h := sha256.New()
	count, err := io.Copy(h, io.LimitReader(file, (256<<20)+1))
	if err != nil {
		return "", err
	}
	if count > 256<<20 {
		return "", fmt.Errorf("judge binary exceeds identity limit")
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}
