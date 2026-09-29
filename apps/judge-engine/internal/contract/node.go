package contract

// 节点控制协议唯一真源：contracts/judge-node.schema.json。
// NodeRegistration 只带节点身份：nodeId 加本次进程的 sessionId，外加访问地址和能判的语言。
// 不上报机器事实，也没有「环境」分组：控制面按节点路由，标定按题目版本 × 语言。
type NodeRegistration struct {
	NodeID    string   `json:"nodeId"`
	SessionID string   `json:"sessionId"`
	Endpoint  string   `json:"endpoint"`
	Languages []string `json:"languages"`
}
type NodeHeartbeat struct {
	NodeID    string `json:"nodeId"`
	SessionID string `json:"sessionId"`
}
type NodeLease struct {
	NodeID          string `json:"nodeId"`
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
	NodeID            string           `json:"nodeId"`
	SessionID         string           `json:"sessionId"`
	TestDataVersionID string           `json:"testDataVersionId"`
	ExpectedSHA256    string           `json:"expectedSha256"`
	Manifest          TestDataManifest `json:"manifest"`
}
type NodeReceipt struct {
	NodeID            string `json:"nodeId"`
	SessionID         string `json:"sessionId"`
	TestDataVersionID string `json:"testDataVersionId"`
	SHA256            string `json:"sha256"`
	FileCount         int    `json:"fileCount"`
}
