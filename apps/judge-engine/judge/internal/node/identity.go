package node

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"runtime"
	"strings"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/language"
)

const (
	uuidBytes              = 16
	uuidVersionMask        = 0x0f
	uuidVersion4           = 0x40
	uuidVariantMask        = 0x3f
	uuidVariant            = 0x80
	maxIdentityBinaryBytes = 256 << 20
)

// 指纹字段、序列化顺序与身份脱敏规则必须同步；不包含节点位置和本次 session。
func newRegistration(j config.JudgeConfig) (contract.NodeRegistration, error) {
	session := make([]byte, uuidBytes)
	if _, err := rand.Read(session); err != nil {
		return contract.NodeRegistration{}, err
	}
	session[6] = (session[6] & uuidVersionMask) | uuidVersion4
	session[8] = (session[8] & uuidVariantMask) | uuidVariant
	id := fmt.Sprintf("%x-%x-%x-%x-%x", session[0:4], session[4:6], session[6:8], session[8:10], session[10:16])
	policy := j
	policy.Node = config.NodeConfig{}
	policy.HTTPAddr = ""
	policy.SandboxURL = ""
	policy.TestdataRoot = ""
	policy.EnvironmentFingerprint = ""
	binaryDigest, err := executableDigest()
	if err != nil {
		return contract.NodeRegistration{}, err
	}
	policy.Node.RuntimeDigest = j.Node.RuntimeDigest + "/judge/" + binaryDigest
	encoded, err := json.Marshal(policy)
	if err != nil {
		return contract.NodeRegistration{}, err
	}
	digest := sha256.Sum256(encoded)
	cpp, _ := language.Get("cpp")
	langJSON, err := json.Marshal(cpp)
	if err != nil {
		return contract.NodeRegistration{}, err
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
		return contract.NodeRegistration{}, err
	}
	fingerprint := sha256.Sum256(fingerprintJSON)
	registration.EnvironmentFingerprint = hex.EncodeToString(fingerprint[:])
	return registration, nil
}

// 可执行文件摘要参与环境身份；相同版本标签下的代码变化也必须改变指纹。
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
	count, err := io.Copy(h, io.LimitReader(file, maxIdentityBinaryBytes+1))
	if err != nil {
		return "", err
	}
	if count > maxIdentityBinaryBytes {
		return "", fmt.Errorf("judge binary exceeds identity limit")
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}
