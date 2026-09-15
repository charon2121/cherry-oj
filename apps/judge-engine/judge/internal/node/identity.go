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

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/config"
	"cherry-oj/judge-engine/judge/internal/language"
)

const (
	uuidBytes              = 16
	uuidVersionMask        = 0x0f
	uuidVersion4           = 0x40
	uuidVariantMask        = 0x3f
	uuidVariant            = 0x80
	maxIdentityBinaryBytes = 256 << 20
	judgeVersion           = "0.1.0-mvp"
)

// policyFingerprint 是进入环境身份的判题策略，**逐项显式列出**。
//
// 此前这里序列化的是整个 judge 配置结构，而该结构没有 JSON 字段名标注，摘要实际依赖
// Go 的字段名与声明顺序——于是给配置加任何一个字段都会静默轮换全网节点的身份，
// 而这条耦合没有写在任何文档或测试里。现在要让某一项参与身份，必须显式加进本结构，
// 并更新 TestConfigDigestIsPinned 的基准值。
//
// 取舍标准是「改了它，同一份提交的判题结论会不会变」：
//   - 收录：空白严格度、是否回传标准答案、墙钟倍率、输出与编译上限、回传截断长度，
//     以及覆盖二进制与资源配额的运行时摘要。
//   - 不收录 inlineThresholdBytes：它只决定测例走内联还是走 store，是纯传输优化，
//     改它不改变任何判题结论。
//   - 不收录 sandboxTimeout：它是调用方的等待上限，描述的不是这个环境的能力。
type policyFingerprint struct {
	StrictWhitespace    bool   `json:"strictWhitespace"`
	RevealExpected      bool   `json:"revealExpected"`
	ClockRatio          int64  `json:"clockRatio"`
	OutputExcerptBytes  int    `json:"outputExcerptBytes"`
	MessageExcerptBytes int    `json:"messageExcerptBytes"`
	StdoutMaxBytes      int64  `json:"stdoutMaxBytes"`
	StderrMaxBytes      int64  `json:"stderrMaxBytes"`
	CompileCPUNs        int64  `json:"compileCpuNs"`
	CompileMemoryBytes  int64  `json:"compileMemoryBytes"`
	CompileClockNs      int64  `json:"compileClockNs"`
	RuntimeDigest       string `json:"runtimeDigest"`
}

func policyOf(s config.Settings, runtimeDigest string) policyFingerprint {
	return policyFingerprint{
		StrictWhitespace:    s.StrictWhitespace,
		RevealExpected:      s.RevealExpected,
		ClockRatio:          s.ClockRatio,
		OutputExcerptBytes:  s.OutputExcerptBytes,
		MessageExcerptBytes: s.MessageExcerptBytes,
		StdoutMaxBytes:      s.Output.StdoutMaxBytes,
		StderrMaxBytes:      s.Output.StderrMaxBytes,
		CompileCPUNs:        s.Compile.CPUNs,
		CompileMemoryBytes:  s.Compile.MemoryBytes,
		CompileClockNs:      s.Compile.ClockNs,
		RuntimeDigest:       runtimeDigest,
	}
}

// configDigest 只依赖 policyFingerprint 的取值，与服务配置结构的形状无关。
func configDigest(p policyFingerprint) (string, error) {
	encoded, err := json.Marshal(p)
	if err != nil {
		return "", err
	}
	sum := sha256.Sum256(encoded)
	return hex.EncodeToString(sum[:]), nil
}

// 指纹字段、序列化顺序与身份脱敏规则必须同步；不包含节点位置和本次 session。
func newRegistration(s config.Settings, env Environment) (contract.NodeRegistration, error) {
	session, err := sessionID()
	if err != nil {
		return contract.NodeRegistration{}, err
	}
	binaryDigest, err := executableDigest()
	if err != nil {
		return contract.NodeRegistration{}, err
	}
	digest, err := configDigest(policyOf(s, env.RuntimeDigest+"/judge/"+binaryDigest))
	if err != nil {
		return contract.NodeRegistration{}, err
	}
	languages, err := declaredLanguages(env.ToolchainVersion)
	if err != nil {
		return contract.NodeRegistration{}, err
	}
	architecture := env.Architecture
	if architecture == "" {
		architecture = runtime.GOARCH
	}
	registration := contract.NodeRegistration{
		NodeID: s.Node.ID, SessionID: session,
		Endpoint:     strings.TrimRight(s.Node.AdvertiseURL, "/"),
		Architecture: architecture, CPUModel: env.CPUModel,
		OSVersion: env.OSVersion, KernelVersion: env.KernelVersion,
		JudgeVersion: judgeVersion, SandboxVersion: env.SandboxVersion,
		ConfigDigest: digest, Languages: languages,
	}
	fingerprint, err := environmentFingerprint(registration)
	if err != nil {
		return contract.NodeRegistration{}, err
	}
	registration.EnvironmentFingerprint = fingerprint
	return registration, nil
}

// environmentFingerprint 脱掉节点位置与本次会话：同一环境的两个节点必须得到同一指纹。
func environmentFingerprint(r contract.NodeRegistration) (string, error) {
	r.NodeID, r.SessionID, r.Endpoint, r.EnvironmentFingerprint = "", "", "", ""
	encoded, err := json.Marshal(r)
	if err != nil {
		return "", err
	}
	sum := sha256.Sum256(encoded)
	return hex.EncodeToString(sum[:]), nil
}

// declaredLanguages 遍历语言注册表，不手写清单——手写的那份迟早和注册表对不上。
func declaredLanguages(toolchain string) ([]contract.NodeLanguage, error) {
	all := language.All()
	declared := make([]contract.NodeLanguage, 0, len(all))
	for _, lang := range all {
		encoded, err := json.Marshal(lang)
		if err != nil {
			return nil, err
		}
		sum := sha256.Sum256(encoded)
		declared = append(declared, contract.NodeLanguage{
			LanguageID:           lang.Name,
			ToolchainVersion:     toolchain,
			LanguageConfigDigest: hex.EncodeToString(sum[:]),
		})
	}
	return declared, nil
}

func sessionID() (string, error) {
	b := make([]byte, uuidBytes)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	b[6] = (b[6] & uuidVersionMask) | uuidVersion4
	b[8] = (b[8] & uuidVariantMask) | uuidVariant
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16]), nil
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
