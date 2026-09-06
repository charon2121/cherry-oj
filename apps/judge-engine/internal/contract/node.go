package contract

// 节点控制协议唯一真源：contracts/judge-node.schema.json。
type NodeLanguage struct {
	LanguageID           string `json:"languageId"`
	ToolchainVersion     string `json:"toolchainVersion"`
	LanguageConfigDigest string `json:"languageConfigDigest"`
}
type NodeRegistration struct {
	NodeID                 string         `json:"nodeId"`
	EnvironmentFingerprint string         `json:"environmentFingerprint"`
	SessionID              string         `json:"sessionId"`
	Endpoint               string         `json:"endpoint"`
	Architecture           string         `json:"architecture"`
	CPUModel               string         `json:"cpuModel"`
	OSVersion              string         `json:"osVersion"`
	KernelVersion          string         `json:"kernelVersion"`
	JudgeVersion           string         `json:"judgeVersion"`
	SandboxVersion         string         `json:"sandboxVersion"`
	ConfigDigest           string         `json:"configDigest"`
	Languages              []NodeLanguage `json:"languages"`
}
type NodeHeartbeat struct {
	NodeID                 string `json:"nodeId"`
	EnvironmentFingerprint string `json:"environmentFingerprint"`
	SessionID              string `json:"sessionId"`
}
type NodeLease struct {
	NodeID          string `json:"nodeId"`
	EnvironmentID   string `json:"environmentId"`
	LeaseDurationNs int64  `json:"leaseDurationNs"`
}
type ManifestFile struct {
	Name      string `json:"name"`
	SizeBytes int64  `json:"sizeBytes"`
	SHA256    string `json:"sha256"`
}
type TestDataManifest struct {
	CaseCount  int            `json:"caseCount"`
	TotalBytes int64          `json:"totalBytes"`
	Files      []ManifestFile `json:"files"`
}
type NodeInstall struct {
	NodeID                 string           `json:"nodeId"`
	EnvironmentFingerprint string           `json:"environmentFingerprint"`
	SessionID              string           `json:"sessionId"`
	TestDataVersionID      string           `json:"testDataVersionId"`
	ExpectedSHA256         string           `json:"expectedSha256"`
	Manifest               TestDataManifest `json:"manifest"`
}
type NodeReceipt struct {
	NodeID                 string `json:"nodeId"`
	EnvironmentFingerprint string `json:"environmentFingerprint"`
	SessionID              string `json:"sessionId"`
	TestDataVersionID      string `json:"testDataVersionId"`
	SHA256                 string `json:"sha256"`
	FileCount              int    `json:"fileCount"`
}
