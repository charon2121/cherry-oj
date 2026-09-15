// Package node 管理 Judge 自注册与节点私有数据安装，不涉及 sandbox 执行语义。
package node

import (
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"syscall"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/config"
)

var errIdentityConflict = errors.New("node identity conflict")

type Node struct {
	cfg          config.Node
	root         string
	registration contract.NodeRegistration
	client       *http.Client
	logger       *slog.Logger
	lock         *os.File
	installMu    sync.Mutex
}

// New 接收配置与已探明的执行环境两个值：身份由两者共同决定，
// 不再由配置结构兼任探测结果的容器。
func New(j config.Settings, env Environment, logger *slog.Logger) (*Node, error) {
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
	registration, err := newRegistration(j, env)
	if err != nil {
		lock.Close()
		return nil, err
	}
	return &Node{cfg: j.Node, root: root, registration: registration, logger: logger, lock: lock, client: &http.Client{Timeout: j.Node.RequestTimeout.Std(), CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}}, nil
}
func (n *Node) Close() error { return n.lock.Close() }
func (n *Node) Registration() contract.NodeRegistration {
	r := n.registration
	r.Languages = append([]contract.NodeLanguage(nil), r.Languages...)
	return r
}
