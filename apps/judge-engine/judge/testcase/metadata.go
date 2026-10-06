package testcase

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"regexp"
)

// MetadataFile 是测试数据目录里的元数据文件名。写入方最后写它，读不到就说明数据还没就绪。
const MetadataFile = "testdata.json"

const (
	metadataSchemaVersion = 1
	maxMetadataBytes      = 1 << 20
	maxCases              = 1000
)

// 测试点名字会拼进本地路径和 URL，必须先用正则关死；加上 .in / .out 后不超过 128 个字符。
var (
	namePattern   = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,123}$`)
	sha256Pattern = regexp.MustCompile(`^[a-f0-9]{64}$`)
)

// fileMetadata、caseMetadata、metadata 与 docs/testdata-protocol.md 的 testdata.json 一一对应。
type fileMetadata struct {
	SizeBytes int64  `json:"sizeBytes"`
	SHA256    string `json:"sha256"`
}

type caseMetadata struct {
	Name   string       `json:"name"`
	Input  fileMetadata `json:"input"`
	Output fileMetadata `json:"output"`
}

type metadata struct {
	SchemaVersion int            `json:"schemaVersion"`
	CaseCount     int            `json:"caseCount"`
	TotalBytes    int64          `json:"totalBytes"`
	Digest        string         `json:"digest"`
	Cases         []caseMetadata `json:"cases"`
}

func (c caseMetadata) inputFile() string  { return c.Name + ".in" }
func (c caseMetadata) outputFile() string { return c.Name + ".out" }

// parseMetadata 严格解析：未知字段、多余内容、不满足协议的取值一律拒绝，而不是尽量读下去。
func parseMetadata(data []byte) (metadata, error) {
	var m metadata
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&m); err != nil {
		return metadata{}, fmt.Errorf("parse %s: %w", MetadataFile, err)
	}
	if _, err := decoder.Token(); err != io.EOF {
		return metadata{}, fmt.Errorf("parse %s: unexpected content after the JSON object", MetadataFile)
	}
	if err := m.validate(); err != nil {
		return metadata{}, fmt.Errorf("invalid %s: %w", MetadataFile, err)
	}
	return m, nil
}

func (m metadata) validate() error {
	if m.SchemaVersion != metadataSchemaVersion {
		return fmt.Errorf("schemaVersion is %d, only %d is supported", m.SchemaVersion, metadataSchemaVersion)
	}
	if m.CaseCount < 1 || m.CaseCount > maxCases {
		return fmt.Errorf("caseCount must be between 1 and %d, got %d", maxCases, m.CaseCount)
	}
	if len(m.Cases) != m.CaseCount {
		return fmt.Errorf("caseCount is %d but cases has %d entries", m.CaseCount, len(m.Cases))
	}
	seen := make(map[string]bool, len(m.Cases))
	var total int64
	for i, c := range m.Cases {
		if !namePattern.MatchString(c.Name) {
			return fmt.Errorf("cases[%d].name %q does not match %s", i, c.Name, namePattern)
		}
		if seen[c.Name] {
			return fmt.Errorf("cases[%d].name %q is duplicated", i, c.Name)
		}
		seen[c.Name] = true
		for _, f := range []struct {
			field string
			meta  fileMetadata
		}{{"input", c.Input}, {"output", c.Output}} {
			if f.meta.SizeBytes < 0 {
				return fmt.Errorf("cases[%d].%s.sizeBytes must not be negative, got %d", i, f.field, f.meta.SizeBytes)
			}
			if !sha256Pattern.MatchString(f.meta.SHA256) {
				return fmt.Errorf("cases[%d].%s.sha256 must be 64 lowercase hex digits", i, f.field)
			}
			total += f.meta.SizeBytes
		}
	}
	if total != m.TotalBytes {
		return fmt.Errorf("totalBytes is %d but the files add up to %d", m.TotalBytes, total)
	}
	if want := m.computeDigest(); m.Digest != want {
		return fmt.Errorf("digest is %q but the cases give %q", m.Digest, want)
	}
	return nil
}

// computeDigest 对 `sha256sum` 风格的行取 SHA-256，顺序即 cases 的顺序，所以顺序也是内容指纹的一部分：
//
//	<input sha256>  <name>.in
//	<output sha256>  <name>.out
func (m metadata) computeDigest() string {
	h := sha256.New()
	for _, c := range m.Cases {
		fmt.Fprintf(h, "%s  %s\n%s  %s\n", c.Input.SHA256, c.inputFile(), c.Output.SHA256, c.outputFile())
	}
	return hex.EncodeToString(h.Sum(nil))
}
