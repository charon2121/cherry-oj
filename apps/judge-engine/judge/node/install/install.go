package install

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

// rejected 与 conflicted 给两类失败附上具体原因。调用方仍用 errors.Is 区分类别并返回
// 同样的响应码；原因只进服务端日志，让管理员知道测试数据包违反了哪一条规则。
func rejected(format string, args ...any) error {
	return fmt.Errorf("%w: "+format, append([]any{errRejected}, args...)...)
}

func conflicted(format string, args ...any) error {
	return fmt.Errorf("%w: "+format, append([]any{errConflict}, args...)...)
}

// Install 用节点锁串行化提交；每次事务独占 staging，不把安装中间状态放进 Node。
func (n *Installer) Install(ctx context.Context, m contract.NodeInstall, archive io.Reader) (contract.NodeReceipt, error) {
	n.mu.Lock()
	defer n.mu.Unlock()
	m.Manifest.Files = append([]contract.ManifestFile(nil), m.Manifest.Files...)
	tx := installation{node: n, metadata: m}
	return tx.run(ctx, archive)
}

type installation struct {
	node          *Installer
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
		return empty, conflicted("install targets another node identity or session")
	}
	if !uuidPattern.MatchString(m.TestDataVersionID) || !hashPattern.MatchString(m.ExpectedSHA256) {
		return empty, rejected("testDataVersionId must be a UUID and expectedSha256 a lowercase SHA-256")
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
			return empty, conflicted("existing version path is not a plain directory")
		}
		// 相同 hash 不能掩盖 manifest 改动或磁盘损坏；每次恢复都复核实际文件。
		saved, e := os.ReadFile(filepath.Join(target, ".receipt.json"))
		if e != nil {
			return empty, conflicted("existing version has no readable receipt: %v", e)
		}
		var receipt contract.NodeReceipt
		if json.Unmarshal(saved, &receipt) != nil || receipt.SHA256 != m.ExpectedSHA256 || receipt.EnvironmentFingerprint != m.EnvironmentFingerprint || receipt.NodeID != m.NodeID || receipt.TestDataVersionID != m.TestDataVersionID || receipt.FileCount != len(m.Manifest.Files) {
			return empty, conflicted("existing version was installed with a different receipt")
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
	if count > tx.node.cfg.MaxArchiveBytes {
		return rejected("archive exceeds %d bytes", tx.node.cfg.MaxArchiveBytes)
	}
	if hex.EncodeToString(digest.Sum(nil)) != tx.metadata.ExpectedSHA256 {
		return rejected("archive SHA-256 does not match expectedSha256")
	}

	return nil
}
func (tx *installation) validateManifest(m contract.TestDataManifest) error {
	if len(m.Files) < 2 || len(m.Files) > tx.node.cfg.MaxFiles || m.CaseCount < 1 || m.CaseCount*2 != len(m.Files) || m.TotalBytes < 0 || m.TotalBytes > tx.node.cfg.MaxExpandedBytes {
		return rejected("manifest counts are inconsistent or exceed limits: files=%d cases=%d totalBytes=%d", len(m.Files), m.CaseCount, m.TotalBytes)
	}
	names := map[string]bool{}
	var total int64
	for _, f := range m.Files {
		if !casePattern.MatchString(f.Name) || names[f.Name] || !hashPattern.MatchString(f.SHA256) || f.SizeBytes < 0 || f.SizeBytes > tx.node.cfg.MaxEntryBytes || f.SizeBytes > tx.node.cfg.MaxExpandedBytes-total {
			return rejected("manifest file %q has an invalid name, duplicate, digest or size", f.Name)
		}
		names[f.Name] = true
		total += f.SizeBytes
	}
	if total != m.TotalBytes {
		return rejected("manifest totalBytes %d does not equal the sum of file sizes %d", m.TotalBytes, total)
	}
	for name := range names {
		base := strings.TrimSuffix(strings.TrimSuffix(name, ".in"), ".out")
		if !names[base+".in"] || !names[base+".out"] {
			return rejected("manifest case %q lacks its .in or .out pair", base)
		}
	}
	return nil
}

// extract 把压缩包里的测试点逐个写进 staging。压缩包只能是平铺的测试点文件，或者整体
// 包在唯一一层外层目录里；macOS 打包附带的元数据被忽略；文件集合必须与清单完全一致。
func (tx *installation) extract(ctx context.Context, zipPath, data string, m contract.TestDataManifest) error {
	z, err := zip.OpenReader(zipPath)
	if err != nil {
		return rejected("archive is not a readable zip: %v", err)
	}
	defer z.Close()
	if len(z.File) > tx.node.cfg.MaxFiles {
		return rejected("archive has %d entries, more than %d", len(z.File), tx.node.cfg.MaxFiles)
	}
	expected := map[string]contract.ManifestFile{}
	for _, f := range m.Files {
		expected[f.Name] = f
	}
	layout := archiveLayout{physical: map[string]bool{}}
	seen := map[string]bool{}
	for _, f := range z.File {
		if err := ctx.Err(); err != nil {
			return err
		}
		logical, isCase, err := layout.classify(f)
		if err != nil {
			return err
		}
		if !isCase {
			continue
		}
		spec, ok := expected[logical]
		if !ok || seen[logical] || !casePattern.MatchString(logical) {
			return rejected("archive file %q is not listed in the manifest or appears twice", logical)
		}
		seen[logical] = true
		if err := tx.extractCase(ctx, f, filepath.Join(data, logical), spec); err != nil {
			return err
		}
	}
	if err := layout.checkWrapper(); err != nil {
		return err
	}
	if len(seen) != len(expected) {
		return rejected("archive has %d of the %d manifest files", len(seen), len(expected))
	}
	return nil
}

// archiveLayout 逐条记录压缩包的目录结构，判断每个条目是测试点文件、可忽略的元数据还是违规条目。
type archiveLayout struct {
	physical     map[string]bool
	dirs         []string
	prefixes     []string
	wrapper      string
	rootSelected bool
}

// classify 返回条目对应的测试点文件名；isCase 为 false 表示目录或可忽略的 macOS 元数据。
func (l *archiveLayout) classify(f *zip.File) (logical string, isCase bool, err error) {
	name := f.Name
	if !safePath(name) || l.physical[name] || (!f.FileInfo().IsDir() && !f.Mode().IsRegular()) || f.Mode()&os.ModeSymlink != 0 || f.Flags&1 != 0 {
		return "", false, rejected("archive entry %q is unsafe, duplicated, special or encrypted", name)
	}
	l.physical[name] = true
	candidate := strings.TrimSuffix(name, "/")
	if candidate == "__MACOSX" || strings.HasPrefix(candidate, "__MACOSX/") {
		return "", false, nil
	}
	parts := strings.Split(candidate, "/")
	if len(parts) <= 2 && (parts[len(parts)-1] == ".DS_Store" || strings.HasPrefix(parts[len(parts)-1], "._")) {
		if len(parts) == 2 {
			l.prefixes = append(l.prefixes, parts[0])
		}
		return "", false, nil
	}
	if f.FileInfo().IsDir() {
		l.dirs = append(l.dirs, candidate)
		return "", false, nil
	}
	if len(parts) > 2 {
		return "", false, rejected("archive entry %q is nested more than one directory deep", name)
	}
	current := ""
	if len(parts) == 2 {
		current = parts[0]
		if !wrapperPattern.MatchString(current) {
			return "", false, rejected("archive wrapper directory %q has an invalid name", current)
		}
	}
	if !l.rootSelected {
		l.wrapper = current
		l.rootSelected = true
	} else if l.wrapper != current {
		return "", false, rejected("archive files are spread across more than one top-level directory")
	}
	return parts[len(parts)-1], true, nil
}

// checkWrapper 在看完全部条目后确认：出现过的目录和元数据前缀都只能是那唯一一层外层目录。
func (l *archiveLayout) checkWrapper() error {
	for _, d := range l.dirs {
		if d != l.wrapper || l.wrapper == "" {
			return rejected("archive directory %q is not the single wrapper directory", d)
		}
	}
	for _, p := range l.prefixes {
		if p != l.wrapper {
			return rejected("archive metadata under %q is outside the wrapper directory", p)
		}
	}
	return nil
}

// extractCase 校验单个测试点的大小与压缩比后写入 staging，并设为只读。
func (tx *installation) extractCase(ctx context.Context, f *zip.File, path string, spec contract.ManifestFile) error {
	if f.UncompressedSize64 > uint64(tx.node.cfg.MaxEntryBytes) || (f.UncompressedSize64 > 0 && (f.CompressedSize64 == 0 || float64(f.UncompressedSize64)/float64(f.CompressedSize64) > float64(tx.node.cfg.MaxCompressionRatio))) {
		return rejected("archive file %q exceeds the entry size or compression ratio limit", f.Name)
	}
	input, err := f.Open()
	if err != nil {
		return rejected("archive file %q cannot be opened: %v", f.Name, err)
	}
	output, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
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
	return os.Chmod(path, 0400)
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
			return rejected("read %q: %v", spec.Name, err)
		}
		if r == utf8.RuneError && width == 1 {
			return rejected("%q is not valid UTF-8", spec.Name)
		}
		size += int64(width)
		if size > tx.node.cfg.MaxEntryBytes {
			return rejected("%q exceeds %d bytes", spec.Name, tx.node.cfg.MaxEntryBytes)
		}
	}
	if size != spec.SizeBytes || hex.EncodeToString(h.Sum(nil)) != spec.SHA256 {
		return rejected("%q does not match the manifest size or SHA-256", spec.Name)
	}
	return nil
}
func (tx *installation) verifyInstalled(ctx context.Context, dir string, m contract.TestDataManifest) error {
	entries, err := os.ReadDir(dir)
	if err != nil || len(entries) != len(m.Files)+1 {
		return conflicted("installed version does not hold exactly the manifest files and a receipt")
	}
	for _, f := range m.Files {
		path := filepath.Join(dir, f.Name)
		stat, err := os.Lstat(path)
		if err != nil || !stat.Mode().IsRegular() || stat.Size() != f.SizeBytes {
			return conflicted("installed file %q is missing or changed", f.Name)
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
