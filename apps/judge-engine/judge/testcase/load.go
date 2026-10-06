package testcase

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"time"
)

const (
	defaultMaxFileBytes  = 256 << 20
	defaultMaxTotalBytes = 1 << 30
	defaultFetchTimeout  = 5 * time.Minute
)

// Options 的零值除 WorkRoot 外都是安全的默认：没配置不等于不限制，也不等于限制为 0。
type Options struct {
	// WorkRoot 是本地副本的父目录，每次 Load 在下面建一个子目录，Set.Close 时删除。
	WorkRoot string
	// MaxFileBytes 与 MaxTotalBytes 限制元数据声明的单个文件与全部文件的大小；超限整体拒绝。
	MaxFileBytes  int64
	MaxTotalBytes int64
	// FetchTimeout 是读取单个文件（HTTP 请求）的期限；本地文件不受它约束。
	FetchTimeout time.Duration
}

func (o Options) withDefaults() Options {
	if o.MaxFileBytes <= 0 {
		o.MaxFileBytes = defaultMaxFileBytes
	}
	if o.MaxTotalBytes <= 0 {
		o.MaxTotalBytes = defaultMaxTotalBytes
	}
	if o.FetchTimeout <= 0 {
		o.FetchTimeout = defaultFetchTimeout
	}
	return o
}

// Set 是一次 Load 得到的测试数据：本地副本里的测试点，加上这份数据的指纹。
type Set struct {
	Cases []TestCase
	// Digest 是 testdata.json 的 digest，判题结果带着它，用来追溯这次拿哪份数据判的。
	Digest string
	dir    string
}

// Close 删除本地副本。副本的目录属于这次判题，用完必须关。
func (s Set) Close() error {
	if s.dir == "" {
		return nil
	}
	return os.RemoveAll(s.dir)
}

// errChanged 表示文件与 testdata.json 对不上，多半是读取撞上了写入方替换数据。
var errChanged = errors.New("test data does not match testdata.json")

// Load 按协议读取 location 下的测试数据并复制到本地。
//
// 测试点顺序以 testdata.json 的 cases 为准。文件缺失、大小或 SHA-256 对不上时，重读一次
// testdata.json 并重试一次，容忍读取撞上写入方的替换；testdata.json 本身读不到或不合规不重试。
func Load(ctx context.Context, opts Options, location string) (Set, error) {
	opts = opts.withDefaults()
	if opts.WorkRoot == "" {
		return Set{}, errors.New("test data work root is not configured")
	}
	src, err := newSource(location, opts.FetchTimeout)
	if err != nil {
		return Set{}, err
	}
	for attempt := 1; ; attempt++ {
		meta, err := readMetadata(ctx, src)
		if err != nil {
			return Set{}, fmt.Errorf("load test data from %s: %w", location, err)
		}
		set, err := copyData(ctx, src, meta, opts)
		if err == nil {
			return set, nil
		}
		if attempt == 2 || ctx.Err() != nil || !retryable(err) {
			return Set{}, fmt.Errorf("load test data from %s: %w", location, err)
		}
	}
}

// retryable 判断复制数据时的错误是不是「读取撞上写入方替换数据」：文件对不上、不见了，或者读本地文件时
// 操作系统报错（macOS 上读取撞上符号链接被替换的瞬间会得到 EINVAL）。大小上限、HTTP 状态码这类错误
// 重试也不会好，不在其内。
func retryable(err error) bool {
	var pathError *fs.PathError
	return errors.Is(err, errChanged) || errors.Is(err, fs.ErrNotExist) || errors.As(err, &pathError)
}

func readMetadata(ctx context.Context, src source) (metadata, error) {
	rc, err := src.open(ctx, MetadataFile)
	if err != nil {
		return metadata{}, fmt.Errorf("open %s: %w", MetadataFile, err)
	}
	defer rc.Close()
	data, err := io.ReadAll(io.LimitReader(rc, maxMetadataBytes+1))
	if err != nil {
		return metadata{}, fmt.Errorf("read %s: %w", MetadataFile, err)
	}
	if len(data) > maxMetadataBytes {
		return metadata{}, fmt.Errorf("%s is larger than %d bytes", MetadataFile, maxMetadataBytes)
	}
	return parseMetadata(data)
}

func copyData(ctx context.Context, src source, meta metadata, opts Options) (Set, error) {
	if meta.TotalBytes > opts.MaxTotalBytes {
		return Set{}, fmt.Errorf("test data is %d bytes, over the %d byte limit", meta.TotalBytes, opts.MaxTotalBytes)
	}
	for _, c := range meta.Cases {
		for _, f := range []fileMetadata{c.Input, c.Output} {
			if f.SizeBytes > opts.MaxFileBytes {
				return Set{}, fmt.Errorf("case %q has a %d byte file, over the %d byte limit", c.Name, f.SizeBytes, opts.MaxFileBytes)
			}
		}
	}
	if err := os.MkdirAll(opts.WorkRoot, 0o700); err != nil {
		return Set{}, fmt.Errorf("create work root: %w", err)
	}
	dir, err := os.MkdirTemp(opts.WorkRoot, "run-")
	if err != nil {
		return Set{}, fmt.Errorf("create work directory: %w", err)
	}
	complete := false
	defer func() {
		if !complete {
			os.RemoveAll(dir)
		}
	}()
	set := Set{Digest: meta.Digest, dir: dir}
	for _, c := range meta.Cases {
		if err := ctx.Err(); err != nil {
			return Set{}, err
		}
		in, err := copyFile(ctx, src, dir, c.inputFile(), c.Input)
		if err != nil {
			return Set{}, err
		}
		out, err := copyFile(ctx, src, dir, c.outputFile(), c.Output)
		if err != nil {
			return Set{}, err
		}
		set.Cases = append(set.Cases, TestCase{Name: c.Name, Input: in, Expected: &out})
	}
	complete = true
	return set, nil
}

// copyFile 把一个文件流式复制到 dir，同时算 SHA-256，复制完成后核对大小与摘要。
func copyFile(ctx context.Context, src source, dir, name string, want fileMetadata) (Blob, error) {
	rc, err := src.open(ctx, name)
	if err != nil {
		return Blob{}, fmt.Errorf("open %s: %w", name, err)
	}
	defer rc.Close()

	path := filepath.Join(dir, name)
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o600)
	if err != nil {
		return Blob{}, fmt.Errorf("create %s: %w", path, err)
	}
	hash := sha256.New()
	// 多读一个字节：文件比声明的长时能看出来，而不是悄悄截断。
	n, copyErr := io.Copy(io.MultiWriter(f, hash), io.LimitReader(rc, want.SizeBytes+1))
	if closeErr := f.Close(); copyErr == nil {
		copyErr = closeErr
	}
	if copyErr != nil {
		return Blob{}, fmt.Errorf("copy %s: %w", name, copyErr)
	}
	if n != want.SizeBytes {
		return Blob{}, fmt.Errorf("%w: %s does not have the %d bytes declared", errChanged, name, want.SizeBytes)
	}
	if got := hex.EncodeToString(hash.Sum(nil)); got != want.SHA256 {
		return Blob{}, fmt.Errorf("%w: %s has sha256 %s, declared %s", errChanged, name, got, want.SHA256)
	}
	return Blob{Size: want.SizeBytes, Open: func() (io.ReadCloser, error) { return os.Open(path) }}, nil
}

// PrepareWorkRoot 建好本地副本的父目录，并清掉上一个进程遗留的内容。
// 每个 judge 进程独占自己的 WorkRoot，所以启动时清空是安全的；两个进程不能共用同一个目录。
func PrepareWorkRoot(root string) error {
	if err := os.MkdirAll(root, 0o700); err != nil {
		return fmt.Errorf("create test data work root %s: %w", root, err)
	}
	entries, err := os.ReadDir(root)
	if err != nil {
		return fmt.Errorf("read test data work root %s: %w", root, err)
	}
	var errs []error
	for _, e := range entries {
		if err := os.RemoveAll(filepath.Join(root, e.Name())); err != nil {
			errs = append(errs, err)
		}
	}
	return errors.Join(errs...)
}
