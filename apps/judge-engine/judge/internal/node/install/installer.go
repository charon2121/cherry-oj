package install

import (
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"sync"
	"syscall"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/config"
)

// Installer 拥有节点私有数据根。它在构造时取得目录的独占锁——同一目录被两个节点同时安装，
// 会让两次原子提交互相覆盖，而回执看起来都成功。
type Installer struct {
	cfg          config.Node
	root         string
	registration contract.NodeRegistration
	logger       *slog.Logger
	lock         *os.File
	mu           sync.Mutex
}

// Open 核验数据根并取得独占锁。root 不得经由符号链接到达：
// 否则控制面下发的数据会被写到一个由别处决定的位置。
func Open(cfg config.Node, testdataRoot string, registration contract.NodeRegistration, logger *slog.Logger) (*Installer, error) {
	root, err := filepath.Abs(testdataRoot)
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
	return &Installer{cfg: cfg, root: root, registration: registration, logger: logger, lock: lock}, nil
}

func (n *Installer) Close() error { return n.lock.Close() }

// Root 返回已解析的数据根，供测试与诊断使用。
func (n *Installer) Root() string { return n.root }

// Registration 返回安装回执使用的身份副本。
func (n *Installer) Registration() contract.NodeRegistration {
	r := n.registration
	r.Languages = append([]contract.NodeLanguage(nil), r.Languages...)
	return r
}
