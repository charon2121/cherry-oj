package testcase_test

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"

	"cherry-oj/judge-engine/judge/testcase"
)

// pair 是一个测试点的输入与期望输出。
type pair struct{ name, in, out string }

// golden 数据集的 digest 来自协议文档的算法，由独立脚本算出。Java 与 Go 必须对同一份数据得到同一个值，
// 所以这里把它钉成常量，而不是在测试里重新实现一遍再拿来比。
var golden = []pair{{"1", "1 2\n", "3\n"}, {"2", "100 -7\n", "93\n"}}

const goldenDigest = "6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4"

func sum(s string) string {
	h := sha256.Sum256([]byte(s))
	return hex.EncodeToString(h[:])
}

// metadataFor 按协议为 pairs 生成 testdata.json 的内容（map 形式，方便各用例改坏某个字段）。
func metadataFor(pairs []pair) map[string]any {
	cases := make([]any, 0, len(pairs))
	var lines strings.Builder
	var total int64
	for _, p := range pairs {
		cases = append(cases, map[string]any{
			"name":   p.name,
			"input":  map[string]any{"sizeBytes": len(p.in), "sha256": sum(p.in)},
			"output": map[string]any{"sizeBytes": len(p.out), "sha256": sum(p.out)},
		})
		fmt.Fprintf(&lines, "%s  %s.in\n%s  %s.out\n", sum(p.in), p.name, sum(p.out), p.name)
		total += int64(len(p.in) + len(p.out))
	}
	return map[string]any{
		"schemaVersion": 1,
		"caseCount":     len(pairs),
		"totalBytes":    total,
		"digest":        sum(lines.String()),
		"cases":         cases,
	}
}

func marshal(t *testing.T, v any) []byte {
	t.Helper()
	b, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}
	return b
}

// writeDataset 在 dir 下按协议写出数据文件和 testdata.json（元数据最后写，与协议对写入方的要求一致）。
func writeDataset(t *testing.T, dir string, pairs []pair, meta map[string]any) {
	t.Helper()
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	for _, p := range pairs {
		for name, content := range map[string]string{p.name + ".in": p.in, p.name + ".out": p.out} {
			if err := os.WriteFile(filepath.Join(dir, name), []byte(content), 0o644); err != nil {
				t.Fatal(err)
			}
		}
	}
	if err := os.WriteFile(filepath.Join(dir, "testdata.json"), marshal(t, meta), 0o644); err != nil {
		t.Fatal(err)
	}
}

func newDataset(t *testing.T, pairs []pair) (dir string) {
	t.Helper()
	dir = filepath.Join(t.TempDir(), "problem")
	writeDataset(t, dir, pairs, metadataFor(pairs))
	return dir
}

func options(t *testing.T) testcase.Options {
	t.Helper()
	return testcase.Options{WorkRoot: filepath.Join(t.TempDir(), "work")}
}

func entries(t *testing.T, dir string) []string {
	t.Helper()
	list, err := os.ReadDir(dir)
	if err != nil && !os.IsNotExist(err) {
		t.Fatal(err)
	}
	names := make([]string, len(list))
	for i, e := range list {
		names[i] = e.Name()
	}
	return names
}

func names(cases []testcase.TestCase) []string {
	out := make([]string, len(cases))
	for i, c := range cases {
		out[i] = c.Name
	}
	return out
}

func TestLoadLocalDirectory(t *testing.T) {
	set, err := testcase.Load(context.Background(), options(t), newDataset(t, golden))
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	defer set.Close()

	if len(set.Cases) != 2 {
		t.Fatalf("测试点数 = %d, want 2", len(set.Cases))
	}
	for i, want := range golden {
		c := set.Cases[i]
		if c.Name != want.name {
			t.Errorf("cases[%d].Name = %q, want %q", i, c.Name, want.name)
		}
		if got := read(t, c.Input); got != want.in {
			t.Errorf("cases[%d] input = %q, want %q", i, got, want.in)
		}
		if c.Expected == nil {
			t.Fatalf("cases[%d].Expected 不该为 nil", i)
		}
		if got := read(t, *c.Expected); got != want.out {
			t.Errorf("cases[%d] expected = %q, want %q", i, got, want.out)
		}
		// Size 必须和文件真实大小一致 —— flow 靠它决定内联还是走 store ref
		if c.Input.Size != int64(len(want.in)) || c.Expected.Size != int64(len(want.out)) {
			t.Errorf("cases[%d] 的 Size = %d/%d, want %d/%d", i, c.Input.Size, c.Expected.Size, len(want.in), len(want.out))
		}
	}
}

// 顺序以 testdata.json 为准，读取方不再按文件名推断。
func TestLoadKeepsTheOrderOfTestdataJSON(t *testing.T) {
	pairs := []pair{{"10", "a", "b"}, {"2", "c", "d"}, {"1", "e", "f"}, {"big-1", "g", "h"}}
	set, err := testcase.Load(context.Background(), options(t), newDataset(t, pairs))
	if err != nil {
		t.Fatal(err)
	}
	defer set.Close()
	if got, want := strings.Join(names(set.Cases), ","), "10,2,1,big-1"; got != want {
		t.Errorf("顺序 = %s, want %s（数值排序或字符串排序都不对，要照 cases 的顺序）", got, want)
	}
}

func TestLoadReturnsTheDigestOfTheData(t *testing.T) {
	set, err := testcase.Load(context.Background(), options(t), newDataset(t, golden))
	if err != nil {
		t.Fatal(err)
	}
	defer set.Close()
	if set.Digest != goldenDigest {
		t.Errorf("Digest = %s, want %s", set.Digest, goldenDigest)
	}
}

// ★ 本地副本是一份快照：复制完成后写入方再改动地址，不能影响这次判题。
// Close 要把副本整个删掉，否则每次判题都在磁盘上留一份数据。
func TestLoadCopiesDataAndCloseRemovesIt(t *testing.T) {
	dir := newDataset(t, golden)
	opts := options(t)
	set, err := testcase.Load(context.Background(), opts, dir)
	if err != nil {
		t.Fatal(err)
	}

	if err := os.RemoveAll(dir); err != nil {
		t.Fatal(err)
	}
	if got := read(t, set.Cases[0].Input); got != "1 2\n" {
		t.Errorf("原目录删除后读到 %q，副本应当独立", got)
	}
	if left := entries(t, opts.WorkRoot); len(left) != 1 {
		t.Fatalf("判题期间工作目录里应有一份副本，得到 %v", left)
	}

	if err := set.Close(); err != nil {
		t.Fatal(err)
	}
	if left := entries(t, opts.WorkRoot); len(left) != 0 {
		t.Errorf("Close 之后工作目录里还剩 %v", left)
	}
}

// Blob 可以被打开多次 —— flow 里重试或先探大小再读都需要这一点
func TestBlobIsReopenable(t *testing.T) {
	set, err := testcase.Load(context.Background(), options(t), newDataset(t, golden))
	if err != nil {
		t.Fatal(err)
	}
	defer set.Close()
	first := read(t, set.Cases[0].Input)
	second := read(t, set.Cases[0].Input)
	if first != second {
		t.Errorf("两次 Open 读到的内容不同: %q vs %q", first, second)
	}
}

func TestLoadRejectsInvalidMetadata(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(m map[string]any)
		want   string
	}{
		{"协议版本不认识", func(m map[string]any) { m["schemaVersion"] = 2 }, "schemaVersion"},
		{"缺少协议版本", func(m map[string]any) { delete(m, "schemaVersion") }, "schemaVersion"},
		{"caseCount 与 cases 不一致", func(m map[string]any) { m["caseCount"] = 3 }, "caseCount"},
		{"caseCount 为 0", func(m map[string]any) { m["caseCount"] = 0; m["cases"] = []any{} }, "caseCount"},
		{"caseCount 超过 1000", func(m map[string]any) { m["caseCount"] = 1001 }, "caseCount"},
		{"totalBytes 对不上", func(m map[string]any) { m["totalBytes"] = 1 }, "totalBytes"},
		{"digest 对不上", func(m map[string]any) { m["digest"] = strings.Repeat("0", 64) }, "digest"},
		{"有未知字段", func(m map[string]any) { m["extra"] = true }, "extra"},
		{"名字含路径分隔符", func(m map[string]any) { m["cases"].([]any)[0].(map[string]any)["name"] = "a/b" }, "name"},
		{"名字向上跳目录", func(m map[string]any) { m["cases"].([]any)[0].(map[string]any)["name"] = "../x" }, "name"},
		{"名字以点开头", func(m map[string]any) { m["cases"].([]any)[0].(map[string]any)["name"] = ".hidden" }, "name"},
		{"名字重复", func(m map[string]any) { m["cases"].([]any)[1].(map[string]any)["name"] = "1" }, "duplicated"},
		{"sha256 不是小写十六进制", func(m map[string]any) {
			m["cases"].([]any)[0].(map[string]any)["input"].(map[string]any)["sha256"] = strings.Repeat("A", 64)
		}, "sha256"},
		{"文件大小为负", func(m map[string]any) {
			m["cases"].([]any)[0].(map[string]any)["output"].(map[string]any)["sizeBytes"] = -1
		}, "sizeBytes"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			meta := metadataFor(golden)
			tt.mutate(meta)
			dir := filepath.Join(t.TempDir(), "problem")
			writeDataset(t, dir, golden, meta)
			opts := options(t)

			_, err := testcase.Load(context.Background(), opts, dir)
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("err = %v, want it to mention %q", err, tt.want)
			}
			if left := entries(t, opts.WorkRoot); len(left) != 0 {
				t.Errorf("失败后工作目录里还剩 %v", left)
			}
		})
	}
}

func TestLoadRejectsUnparsableMetadata(t *testing.T) {
	for name, content := range map[string]string{
		"不是 JSON":   "not json",
		"JSON 后有多余": string(marshal(t, metadataFor(golden))) + ` {}`,
	} {
		t.Run(name, func(t *testing.T) {
			dir := filepath.Join(t.TempDir(), "problem")
			writeDataset(t, dir, golden, metadataFor(golden))
			if err := os.WriteFile(filepath.Join(dir, "testdata.json"), []byte(content), 0o644); err != nil {
				t.Fatal(err)
			}
			if _, err := testcase.Load(context.Background(), options(t), dir); err == nil {
				t.Fatal("期望报错")
			}
		})
	}
}

// 读不到 testdata.json 就是数据未就绪，整次失败，且错误里点名这个文件。
func TestLoadWithoutMetadataFails(t *testing.T) {
	dir := newDataset(t, golden)
	if err := os.Remove(filepath.Join(dir, "testdata.json")); err != nil {
		t.Fatal(err)
	}
	_, err := testcase.Load(context.Background(), options(t), dir)
	if err == nil || !strings.Contains(err.Error(), "testdata.json") {
		t.Fatalf("err = %v, want it to mention testdata.json", err)
	}
}

// 数据文件缺失、被截断、被改写：整体拒绝，不能只判一部分。
func TestLoadRejectsFilesThatDoNotMatchTheMetadata(t *testing.T) {
	tests := []struct {
		name   string
		change func(t *testing.T, dir string)
		want   string
	}{
		{"缺少输入文件", func(t *testing.T, dir string) { os.Remove(filepath.Join(dir, "2.in")) }, "2.in"},
		{"缺少答案文件", func(t *testing.T, dir string) { os.Remove(filepath.Join(dir, "1.out")) }, "1.out"},
		{"文件被截断", func(t *testing.T, dir string) { os.WriteFile(filepath.Join(dir, "1.out"), []byte("3"), 0o644) }, "1.out"},
		{"文件变长", func(t *testing.T, dir string) { os.WriteFile(filepath.Join(dir, "1.out"), []byte("3\n\n\n"), 0o644) }, "1.out"},
		{"大小相同内容不同", func(t *testing.T, dir string) { os.WriteFile(filepath.Join(dir, "1.out"), []byte("4\n"), 0o644) }, "sha256"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dir := newDataset(t, golden)
			tt.change(t, dir)
			opts := options(t)

			_, err := testcase.Load(context.Background(), opts, dir)
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("err = %v, want it to mention %q", err, tt.want)
			}
			if left := entries(t, opts.WorkRoot); len(left) != 0 {
				t.Errorf("失败后工作目录里还剩 %v", left)
			}
		})
	}
}

func TestLoadEnforcesSizeLimits(t *testing.T) {
	dir := newDataset(t, golden) // 最大的文件 7 字节，全部 16 字节
	t.Run("单个文件超限", func(t *testing.T) {
		opts := options(t)
		opts.MaxFileBytes = 6
		if _, err := testcase.Load(context.Background(), opts, dir); err == nil || !strings.Contains(err.Error(), "limit") {
			t.Fatalf("err = %v, want a limit error", err)
		}
	})
	t.Run("总量超限", func(t *testing.T) {
		opts := options(t)
		opts.MaxTotalBytes = 15
		if _, err := testcase.Load(context.Background(), opts, dir); err == nil || !strings.Contains(err.Error(), "limit") {
			t.Fatalf("err = %v, want a limit error", err)
		}
	})
	t.Run("恰好等于上限可以通过", func(t *testing.T) {
		opts := options(t)
		opts.MaxFileBytes, opts.MaxTotalBytes = 7, 16
		set, err := testcase.Load(context.Background(), opts, dir)
		if err != nil {
			t.Fatalf("上限是闭区间: %v", err)
		}
		set.Close()
	})
}

func TestLoadRejectsUnsupportedLocations(t *testing.T) {
	for _, location := range []string{
		"", "problem/data", "./data", "file:///data", "ftp://host/data", "s3://bucket/data",
		"http://", "http://user:secret@host/data", "http://host/data?token=1", "http://host/data#x",
	} {
		t.Run(location, func(t *testing.T) {
			_, err := testcase.Load(context.Background(), options(t), location)
			if err == nil {
				t.Fatal("期望报错")
			}
			if strings.Contains(err.Error(), "secret") {
				t.Errorf("错误信息泄漏了凭据: %v", err)
			}
		})
	}
}

func TestLoadRequiresAWorkRoot(t *testing.T) {
	if _, err := testcase.Load(context.Background(), testcase.Options{}, newDataset(t, golden)); err == nil {
		t.Fatal("没有配置工作目录应当报错，而不是悄悄用系统临时目录")
	}
}

func TestLoadHonorsCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	opts := options(t)
	if _, err := testcase.Load(ctx, opts, newDataset(t, golden)); err == nil {
		t.Fatal("上下文已取消应当报错")
	}
	if left := entries(t, opts.WorkRoot); len(left) != 0 {
		t.Errorf("取消后工作目录里还剩 %v", left)
	}
}

func TestPrepareWorkRootClearsLeftovers(t *testing.T) {
	root := filepath.Join(t.TempDir(), "work")
	if err := os.MkdirAll(filepath.Join(root, "run-crashed"), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "run-crashed", "1.in"), []byte("x"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := testcase.PrepareWorkRoot(root); err != nil {
		t.Fatal(err)
	}
	if left := entries(t, root); len(left) != 0 {
		t.Errorf("启动时应清掉上个进程遗留的副本，还剩 %v", left)
	}
	if err := testcase.PrepareWorkRoot(filepath.Join(root, "nested", "new")); err != nil {
		t.Errorf("目录不存在时应当创建: %v", err)
	}
}

// server 是一个可以编排「某次请求返回什么」的 HTTP 测试数据服务，并记录每个路径被请求了几次。
type server struct {
	*httptest.Server
	mu   sync.Mutex
	hits map[string]int
}

func (s *server) hitCount(path string) int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.hits[path]
}

// serve 返回的 handler 每次先登记请求，再交给 answer（参数是路径和该路径的第几次请求）。
func serve(t *testing.T, answer func(w http.ResponseWriter, path string, nth int)) *server {
	t.Helper()
	s := &server{hits: map[string]int{}}
	s.Server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		s.mu.Lock()
		s.hits[r.URL.Path]++
		nth := s.hits[r.URL.Path]
		s.mu.Unlock()
		answer(w, r.URL.Path, nth)
	}))
	t.Cleanup(s.Close)
	return s
}

// files 把一份数据集变成路径到内容的表，路径前缀是 /data。
func files(t *testing.T, pairs []pair) map[string]string {
	t.Helper()
	out := map[string]string{"/data/testdata.json": string(marshal(t, metadataFor(pairs)))}
	for _, p := range pairs {
		out["/data/"+p.name+".in"] = p.in
		out["/data/"+p.name+".out"] = p.out
	}
	return out
}

func respond(w http.ResponseWriter, table map[string]string, path string) {
	body, ok := table[path]
	if !ok {
		http.NotFound(w, nil)
		return
	}
	fmt.Fprint(w, body)
}

func TestLoadOverHTTP(t *testing.T) {
	table := files(t, golden)
	s := serve(t, func(w http.ResponseWriter, path string, _ int) { respond(w, table, path) })

	// 末尾有没有 / 都行
	for _, suffix := range []string{"/data", "/data/"} {
		t.Run("地址"+suffix, func(t *testing.T) {
			set, err := testcase.Load(context.Background(), options(t), s.URL+suffix)
			if err != nil {
				t.Fatalf("Load: %v", err)
			}
			defer set.Close()
			if got := read(t, set.Cases[1].Input); got != "100 -7\n" {
				t.Errorf("input = %q", got)
			}
			if set.Digest != goldenDigest {
				t.Errorf("Digest = %s", set.Digest)
			}
		})
	}
}

// 读取撞上写入方替换数据：第一次读到旧的 testdata.json 却拿到新的数据文件，重读后成功。
func TestLoadRetriesOnceWhenDataChangesWhileReading(t *testing.T) {
	oldData := []pair{{"1", "old in\n", "old out\n"}}
	oldTable, newTable := files(t, oldData), files(t, golden)
	s := serve(t, func(w http.ResponseWriter, path string, nth int) {
		if path == "/data/testdata.json" && nth == 1 {
			respond(w, oldTable, path)
			return
		}
		respond(w, newTable, path)
	})

	set, err := testcase.Load(context.Background(), options(t), s.URL+"/data")
	if err != nil {
		t.Fatalf("应当重试一次后成功: %v", err)
	}
	defer set.Close()
	if set.Digest != goldenDigest || len(set.Cases) != 2 {
		t.Errorf("重试后应读到新数据: digest=%s cases=%v", set.Digest, names(set.Cases))
	}
	if n := s.hitCount("/data/testdata.json"); n != 2 {
		t.Errorf("testdata.json 被请求 %d 次，want 2", n)
	}
}

// 重试只有一次：数据始终对不上就失败，不能无限重读。
func TestLoadGivesUpAfterOneRetry(t *testing.T) {
	table := files(t, golden)
	table["/data/1.out"] = "tampered\n"
	s := serve(t, func(w http.ResponseWriter, path string, _ int) { respond(w, table, path) })

	_, err := testcase.Load(context.Background(), options(t), s.URL+"/data")
	if err == nil {
		t.Fatal("期望报错")
	}
	if n := s.hitCount("/data/testdata.json"); n != 2 {
		t.Errorf("testdata.json 被请求 %d 次，want 2（一次初读加一次重试）", n)
	}
	if n := s.hitCount("/data/1.out"); n != 2 {
		t.Errorf("1.out 被请求 %d 次，want 2", n)
	}
}

func TestLoadDoesNotRetryWhenMetadataIsUnavailable(t *testing.T) {
	s := serve(t, func(w http.ResponseWriter, _ string, _ int) { http.NotFound(w, nil) })
	_, err := testcase.Load(context.Background(), options(t), s.URL+"/data")
	if err == nil || !strings.Contains(err.Error(), "testdata.json") {
		t.Fatalf("err = %v, want it to mention testdata.json", err)
	}
	if n := s.hitCount("/data/testdata.json"); n != 1 {
		t.Errorf("testdata.json 被请求 %d 次，want 1（数据未就绪不重试）", n)
	}
}

func TestLoadDoesNotRetryOnServerErrors(t *testing.T) {
	table := files(t, golden)
	s := serve(t, func(w http.ResponseWriter, path string, _ int) {
		if path == "/data/2.in" {
			http.Error(w, "boom", http.StatusInternalServerError)
			return
		}
		respond(w, table, path)
	})
	opts := options(t)
	_, err := testcase.Load(context.Background(), opts, s.URL+"/data")
	if err == nil || !strings.Contains(err.Error(), "500") {
		t.Fatalf("err = %v, want it to mention the status", err)
	}
	if n := s.hitCount("/data/2.in"); n != 1 {
		t.Errorf("2.in 被请求 %d 次，want 1（服务端错误不是数据被替换）", n)
	}
	if left := entries(t, opts.WorkRoot); len(left) != 0 {
		t.Errorf("失败后工作目录里还剩 %v", left)
	}
}
