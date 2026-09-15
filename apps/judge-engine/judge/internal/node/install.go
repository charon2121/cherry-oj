package node

import (
	"archive/zip"
	"bufio"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"unicode"
	"unicode/utf8"

	"cherry-oj/judge-engine/internal/contract"
)

var uuidPattern = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)
var hashPattern = regexp.MustCompile(`^[a-f0-9]{64}$`)
var casePattern = regexp.MustCompile(`^([A-Za-z0-9][A-Za-z0-9._-]{0,127})\.(in|out)$`)
var wrapperPattern = regexp.MustCompile(`^[\p{L}\p{N}][\p{L}\p{N} ._-]{0,127}$`)
var errRejected = errors.New("NODE_DATA_REJECTED")
var errConflict = errors.New("NODE_DATA_CONFLICT")

// Install 用节点锁串行化提交；每次事务独占 staging，不把安装中间状态放进 Node。
func (n *Node) Install(ctx context.Context, m contract.NodeInstall, archive io.Reader) (contract.NodeReceipt, error) {
	n.installMu.Lock()
	defer n.installMu.Unlock()
	m.Manifest.Files = append([]contract.ManifestFile(nil), m.Manifest.Files...)
	tx := installation{node: n, metadata: m}
	return tx.run(ctx, archive)
}

type installation struct {
	node          *Node
	metadata      contract.NodeInstall
	work, zipPath string
}

func (tx *installation) run(ctx context.Context, archive io.Reader) (contract.NodeReceipt, error) {
	m := tx.metadata
	// staging 与目标同文件系统，回执随目录原子提交；失败只移除本事务的 staging。
	defer func() {
		if tx.work != "" {
			_ = os.RemoveAll(tx.work)
		}
	}()
	empty := contract.NodeReceipt{}
	if m.NodeID != tx.node.registration.NodeID || m.EnvironmentFingerprint != tx.node.registration.EnvironmentFingerprint || m.SessionID != tx.node.registration.SessionID {
		return empty, errConflict
	}
	if !uuidPattern.MatchString(m.TestDataVersionID) || !hashPattern.MatchString(m.ExpectedSHA256) {
		return empty, errRejected
	}
	if err := tx.validateManifest(m.Manifest); err != nil {
		return empty, err
	}
	if err := tx.receiveArchive(ctx, archive); err != nil {
		return empty, err
	}
	target := filepath.Join(tx.node.root, m.TestDataVersionID)
	if stat, e := os.Lstat(target); e == nil {
		if !stat.IsDir() || stat.Mode()&os.ModeSymlink != 0 {
			return empty, errConflict
		}
		// 相同 hash 不能掩盖 manifest 改动或磁盘损坏；每次恢复都复核实际文件。
		saved, e := os.ReadFile(filepath.Join(target, ".receipt.json"))
		if e != nil {
			return empty, errConflict
		}
		var receipt contract.NodeReceipt
		if json.Unmarshal(saved, &receipt) != nil || receipt.SHA256 != m.ExpectedSHA256 || receipt.EnvironmentFingerprint != m.EnvironmentFingerprint || receipt.NodeID != m.NodeID || receipt.TestDataVersionID != m.TestDataVersionID || receipt.FileCount != len(m.Manifest.Files) {
			return empty, errConflict
		}
		if err := tx.verifyInstalled(ctx, target, m.Manifest); err != nil {
			return empty, err
		}
		receipt.SessionID = tx.node.registration.SessionID
		return receipt, nil
	} else if !os.IsNotExist(e) {
		return empty, e
	}
	data := filepath.Join(tx.work, "data")
	if err := os.Mkdir(data, 0700); err != nil {
		return empty, err
	}
	if err := tx.extract(ctx, tx.zipPath, data, m.Manifest); err != nil {
		return empty, err
	}
	receipt := contract.NodeReceipt{NodeID: tx.node.registration.NodeID, EnvironmentFingerprint: tx.node.registration.EnvironmentFingerprint, SessionID: tx.node.registration.SessionID, TestDataVersionID: m.TestDataVersionID, SHA256: m.ExpectedSHA256, FileCount: len(m.Manifest.Files)}
	payload, err := json.Marshal(receipt)
	if err != nil {
		return empty, err
	}
	if err := os.WriteFile(filepath.Join(data, ".receipt.json"), payload, 0400); err != nil {
		return empty, err
	}
	if err := ctx.Err(); err != nil {
		return empty, err
	}
	// root 的进程锁 + installMu 保证无另一个安装者覆盖目标。跨文件系统 rename 明确失败。
	if err := os.Rename(data, target); err != nil {
		return empty, fmt.Errorf("atomic install: %w", err)
	}
	return receipt, nil
}
func (tx *installation) receiveArchive(ctx context.Context, archive io.Reader) error {
	work, err := os.MkdirTemp(tx.node.root, ".install-")
	if err != nil {
		return fmt.Errorf("create staging: %w", err)
	}
	tx.work = work
	tx.zipPath = filepath.Join(work, "asset.zip")
	f, err := os.OpenFile(tx.zipPath, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	digest := sha256.New()
	count, copyErr := io.Copy(io.MultiWriter(f, digest), io.LimitReader(&contextReader{ctx, archive}, tx.node.cfg.MaxArchiveBytes+1))
	closeErr := f.Close()
	if copyErr != nil {
		return fmt.Errorf("receive archive: %w", copyErr)
	}
	if closeErr != nil {
		return closeErr
	}
	if count > tx.node.cfg.MaxArchiveBytes || hex.EncodeToString(digest.Sum(nil)) != tx.metadata.ExpectedSHA256 {
		return errRejected
	}

	return nil
}
func (tx *installation) validateManifest(m contract.TestDataManifest) error {
	if len(m.Files) < 2 || len(m.Files) > tx.node.cfg.MaxFiles || m.CaseCount < 1 || m.CaseCount*2 != len(m.Files) || m.TotalBytes < 0 || m.TotalBytes > tx.node.cfg.MaxExpandedBytes {
		return errRejected
	}
	names := map[string]bool{}
	var total int64
	for _, f := range m.Files {
		if !casePattern.MatchString(f.Name) || names[f.Name] || !hashPattern.MatchString(f.SHA256) || f.SizeBytes < 0 || f.SizeBytes > tx.node.cfg.MaxEntryBytes || f.SizeBytes > tx.node.cfg.MaxExpandedBytes-total {
			return errRejected
		}
		names[f.Name] = true
		total += f.SizeBytes
	}
	if total != m.TotalBytes {
		return errRejected
	}
	for name := range names {
		base := strings.TrimSuffix(strings.TrimSuffix(name, ".in"), ".out")
		if !names[base+".in"] || !names[base+".out"] {
			return errRejected
		}
	}
	return nil
}
func (tx *installation) extract(ctx context.Context, zipPath, data string, m contract.TestDataManifest) error {
	z, err := zip.OpenReader(zipPath)
	if err != nil {
		return errRejected
	}
	defer z.Close()
	if len(z.File) > tx.node.cfg.MaxFiles {
		return errRejected
	}
	expected := map[string]contract.ManifestFile{}
	for _, f := range m.Files {
		expected[f.Name] = f
	}
	physical := map[string]bool{}
	seen := map[string]bool{}
	dirs := []string{}
	prefixes := []string{}
	wrapper := ""
	rootSelected := false
	for _, f := range z.File {
		if err := ctx.Err(); err != nil {
			return err
		}
		name := f.Name
		if !safePath(name) || physical[name] || (!f.FileInfo().IsDir() && !f.Mode().IsRegular()) || f.Mode()&os.ModeSymlink != 0 || f.Flags&1 != 0 {
			return errRejected
		}
		physical[name] = true
		candidate := strings.TrimSuffix(name, "/")
		if candidate == "__MACOSX" || strings.HasPrefix(candidate, "__MACOSX/") {
			continue
		}
		parts := strings.Split(candidate, "/")
		if len(parts) <= 2 && (parts[len(parts)-1] == ".DS_Store" || strings.HasPrefix(parts[len(parts)-1], "._")) {
			if len(parts) == 2 {
				prefixes = append(prefixes, parts[0])
			}
			continue
		}
		if f.FileInfo().IsDir() {
			dirs = append(dirs, candidate)
			continue
		}
		if len(parts) > 2 {
			return errRejected
		}
		current := ""
		logical := parts[len(parts)-1]
		if len(parts) == 2 {
			current = parts[0]
			if !wrapperPattern.MatchString(current) {
				return errRejected
			}
		}
		if !rootSelected {
			wrapper = current
			rootSelected = true
		} else if wrapper != current {
			return errRejected
		}
		spec, ok := expected[logical]
		if !ok || seen[logical] || !casePattern.MatchString(logical) {
			return errRejected
		}
		seen[logical] = true
		if f.UncompressedSize64 > uint64(tx.node.cfg.MaxEntryBytes) || (f.UncompressedSize64 > 0 && (f.CompressedSize64 == 0 || float64(f.UncompressedSize64)/float64(f.CompressedSize64) > float64(tx.node.cfg.MaxCompressionRatio))) {
			return errRejected
		}
		input, err := f.Open()
		if err != nil {
			return errRejected
		}
		output, err := os.OpenFile(filepath.Join(data, logical), os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
		if err != nil {
			input.Close()
			return err
		}
		err = tx.copyCase(ctx, input, output, spec)
		inputErr := input.Close()
		outputErr := output.Close()
		if err != nil {
			return err
		}
		if inputErr != nil {
			return inputErr
		}
		if outputErr != nil {
			return outputErr
		}
		if err := os.Chmod(filepath.Join(data, logical), 0400); err != nil {
			return err
		}
	}
	for _, d := range dirs {
		if d != wrapper || wrapper == "" {
			return errRejected
		}
	}
	for _, p := range prefixes {
		if p != wrapper {
			return errRejected
		}
	}
	if len(seen) != len(expected) {
		return errRejected
	}
	return nil
}
func (tx *installation) copyCase(ctx context.Context, input io.Reader, output io.Writer, spec contract.ManifestFile) error {
	h := sha256.New()
	reader := bufio.NewReader(io.TeeReader(io.LimitReader(&contextReader{ctx, input}, tx.node.cfg.MaxEntryBytes+1), io.MultiWriter(output, h)))
	var size int64
	for {
		r, width, err := reader.ReadRune()
		if err == io.EOF {
			break
		}
		if err != nil {
			return errRejected
		}
		if r == utf8.RuneError && width == 1 {
			return errRejected
		}
		size += int64(width)
		if size > tx.node.cfg.MaxEntryBytes {
			return errRejected
		}
	}
	if size != spec.SizeBytes || hex.EncodeToString(h.Sum(nil)) != spec.SHA256 {
		return errRejected
	}
	return nil
}
func (tx *installation) verifyInstalled(ctx context.Context, dir string, m contract.TestDataManifest) error {
	entries, err := os.ReadDir(dir)
	if err != nil || len(entries) != len(m.Files)+1 {
		return errConflict
	}
	for _, f := range m.Files {
		path := filepath.Join(dir, f.Name)
		stat, err := os.Lstat(path)
		if err != nil || !stat.Mode().IsRegular() || stat.Size() != f.SizeBytes {
			return errConflict
		}
		input, err := os.Open(path)
		if err != nil {
			return err
		}
		err = tx.copyCase(ctx, input, io.Discard, f)
		closeErr := input.Close()
		if err != nil {
			return err
		}
		if closeErr != nil {
			return closeErr
		}
	}
	return nil
}
func safePath(name string) bool {
	if len(name) == 0 || len(name) > 512 || !utf8.ValidString(name) || strings.HasPrefix(name, "/") || strings.Contains(name, "\\") {
		return false
	}
	for _, part := range strings.Split(strings.TrimSuffix(name, "/"), "/") {
		if part == "" || part == "." || part == ".." {
			return false
		}
		for _, r := range part {
			if unicode.IsControl(r) {
				return false
			}
		}
	}
	return true
}

type contextReader struct {
	ctx    context.Context
	reader io.Reader
}

func (r *contextReader) Read(p []byte) (int, error) {
	if err := r.ctx.Err(); err != nil {
		return 0, err
	}
	return r.reader.Read(p)
}
