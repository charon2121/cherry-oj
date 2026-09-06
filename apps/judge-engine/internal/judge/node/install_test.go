package node_test

import (
	"archive/zip"
	"bytes"
	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/judge/node"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"testing"
)

func hash(b []byte) string { h := sha256.Sum256(b); return hex.EncodeToString(h[:]) }
func newNode(t *testing.T, root string) (*node.Node, config.JudgeConfig) {
	t.Helper()
	c := config.Default().Judge
	c.TestdataRoot = root
	c.Node.Enabled = true
	c.Node.ControlToken = "test-control-token"
	n, err := node.New(c, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatal(err)
	}
	return n, c
}
func archive(t *testing.T, names []string, contents [][]byte, symlink bool) []byte {
	t.Helper()
	var b bytes.Buffer
	z := zip.NewWriter(&b)
	for i, name := range names {
		h := &zip.FileHeader{Name: name, Method: zip.Deflate}
		h.SetMode(0600)
		if symlink {
			h.SetMode(os.ModeSymlink | 0700)
		}
		w, e := z.CreateHeader(h)
		if e != nil {
			t.Fatal(e)
		}
		if _, e = w.Write(contents[i]); e != nil {
			t.Fatal(e)
		}
	}
	if e := z.Close(); e != nil {
		t.Fatal(e)
	}
	return b.Bytes()
}
func metadata(n *node.Node, b []byte) contract.NodeInstall {
	r := n.Registration()
	return contract.NodeInstall{NodeID: r.NodeID, EnvironmentFingerprint: r.EnvironmentFingerprint, SessionID: r.SessionID, TestDataVersionID: "019c8e42-7f70-7000-8000-000000000002", ExpectedSHA256: hash(b), Manifest: contract.TestDataManifest{CaseCount: 1, TotalBytes: 6, Files: []contract.ManifestFile{{Name: "1.in", SizeBytes: 4, SHA256: hash([]byte("1 2\n"))}, {Name: "1.out", SizeBytes: 2, SHA256: hash([]byte("3\n"))}}}}
}
func TestInstallZIPCompatibilityAndRejectCleanup(t *testing.T) {
	for _, tc := range []struct {
		name  string
		names []string
		data  [][]byte
		link  bool
		ok    bool
	}{
		{"flat", []string{"1.in", "1.out"}, [][]byte{[]byte("1 2\n"), []byte("3\n")}, false, true},
		{"Finder", []string{"测试 data/", "测试 data/1.in", "测试 data/1.out", "__MACOSX/._test", "测试 data/.DS_Store"}, [][]byte{nil, []byte("1 2\n"), []byte("3\n"), {0xff}, {0xff}}, false, true},
		{"traversal", []string{"../1.in", "1.out"}, [][]byte{[]byte("1 2\n"), []byte("3\n")}, false, false},
		{"mixed", []string{"data/1.in", "1.out"}, [][]byte{[]byte("1 2\n"), []byte("3\n")}, false, false},
		{"nested", []string{"data/nested/1.in", "data/nested/1.out"}, [][]byte{[]byte("1 2\n"), []byte("3\n")}, false, false},
		{"duplicate", []string{"1.in", "1.in", "1.out"}, [][]byte{[]byte("1 2\n"), []byte("1 2\n"), []byte("3\n")}, false, false},
		{"orphan", []string{"1.in"}, [][]byte{[]byte("1 2\n")}, false, false},
		{"symlink", []string{"1.in", "1.out"}, [][]byte{[]byte("1 2\n"), []byte("3\n")}, true, false},
		{"invalid UTF8", []string{"1.in", "1.out"}, [][]byte{{0xff}, []byte("3\n")}, false, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			root := t.TempDir()
			n, _ := newNode(t, root)
			defer n.Close()
			b := archive(t, tc.names, tc.data, tc.link)
			m := metadata(n, b)
			_, err := n.Install(context.Background(), m, bytes.NewReader(b))
			if (err == nil) != tc.ok {
				t.Fatalf("err=%v", err)
			}
			entries, e := os.ReadDir(root)
			if e != nil {
				t.Fatal(e)
			}
			for _, f := range entries {
				if f.Name() != ".node.lock" && (!tc.ok || f.Name() != m.TestDataVersionID) {
					t.Fatalf("leftover %s", f.Name())
				}
			}
		})
	}
}
func TestInstallRetryRestartConflictAndCancellation(t *testing.T) {
	root := t.TempDir()
	n, c := newNode(t, root)
	b := archive(t, []string{"1.in", "1.out"}, [][]byte{[]byte("1 2\n"), []byte("3\n")}, false)
	m := metadata(n, b)
	first, err := n.Install(context.Background(), m, bytes.NewReader(b))
	if err != nil {
		t.Fatal(err)
	}
	retry, err := n.Install(context.Background(), m, bytes.NewReader(b))
	if err != nil || retry != first {
		t.Fatalf("retry: %v %v", retry, err)
	}
	bad := m
	bad.Manifest.Files = append([]contract.ManifestFile(nil), m.Manifest.Files...)
	bad.Manifest.Files[0].SHA256 = hash([]byte("no"))
	if _, e := n.Install(context.Background(), bad, bytes.NewReader(b)); e == nil {
		t.Fatal("changed manifest accepted")
	}
	bad = m
	bad.EnvironmentFingerprint = "other"
	if _, e := n.Install(context.Background(), bad, bytes.NewReader(b)); e == nil {
		t.Fatal("wrong environment accepted")
	}
	if err := n.Close(); err != nil {
		t.Fatal(err)
	}
	n, err = node.New(c, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer n.Close()
	m.SessionID = n.Registration().SessionID
	after, err := n.Install(context.Background(), m, bytes.NewReader(b))
	if err != nil || after.SessionID == first.SessionID {
		t.Fatalf("restart: %v", err)
	}
	info, err := os.Stat(filepath.Join(root, m.TestDataVersionID, "1.in"))
	if err != nil || info.Mode().Perm() != 0400 {
		t.Fatal("not private readonly")
	}
	m.TestDataVersionID = "019c8e42-7f70-7000-8000-000000000003"
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := n.Install(ctx, m, bytes.NewReader(b)); err == nil {
		t.Fatal("cancellation ignored")
	}
	if _, err := os.Stat(filepath.Join(root, m.TestDataVersionID)); !os.IsNotExist(err) {
		t.Fatal("cancelled directory left")
	}
}

func TestInstallRejectsLimitsAndArchiveHash(t *testing.T) {
	for _, kind := range []string{"archive", "entry", "expanded", "count", "hash", "ratio"} {
		t.Run(kind, func(t *testing.T) {
			root := t.TempDir()
			c := config.Default().Judge
			c.TestdataRoot = root
			c.Node.Enabled = true
			c.Node.ControlToken = "control"
			switch kind {
			case "archive":
				c.Node.MaxArchiveBytes = 10
			case "entry":
				c.Node.MaxEntryBytes = 1
			case "expanded":
				c.Node.MaxExpandedBytes = 1
			case "count":
				c.Node.MaxFiles = 2
			case "ratio":
				c.Node.MaxCompressionRatio = 1
			}
			n, err := node.New(c, nil)
			if err != nil {
				t.Fatal(err)
			}
			defer n.Close()
			names := []string{"1.in", "1.out"}
			data := [][]byte{[]byte("1 2\n"), []byte("3\n")}
			if kind == "count" {
				names = append(names, ".DS_Store")
				data = append(data, nil)
			}
			if kind == "ratio" {
				data[0] = bytes.Repeat([]byte("a"), 10000)
			}
			b := archive(t, names, data, false)
			m := metadata(n, b)
			if kind == "hash" {
				m.ExpectedSHA256 = hash([]byte("wrong"))
			}
			if _, err := n.Install(context.Background(), m, bytes.NewReader(b)); err == nil {
				t.Fatal("limit accepted")
			}
			entries, err := os.ReadDir(root)
			if err != nil || len(entries) != 1 {
				t.Fatalf("leftovers: %v %v", entries, err)
			}
		})
	}
}
