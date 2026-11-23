package backend

import (
	"bytes"
	"encoding/json"
	"fmt"
)

// executorFacts 是执行器输出的一行 JSON；字段见 docs/protocols/sandbox-executor.md。
type executorFacts struct {
	Version         int                `json:"version"`
	ExitCode        int                `json:"exitCode"`
	Signal          int                `json:"signal"`
	CPUNs           int64              `json:"cpuNs"`
	MemoryBytes     int64              `json:"memoryBytes"`
	ClockNs         int64              `json:"clockNs"`
	Reason          Reason             `json:"reason"`
	OOM             uint64             `json:"oom"`
	OOMKill         uint64             `json:"oomKill"`
	MemoryMaxEvents uint64             `json:"memoryMaxEvents"`
	PidsMaxEvents   uint64             `json:"pidsMaxEvents"`
	Cancelled       bool               `json:"cancelled"`
	OutputExceeded  bool               `json:"outputExceeded"`
	StdoutBytes     int64              `json:"stdoutBytes"`
	StderrBytes     int64              `json:"stderrBytes"`
	Outputs         []executorArtifact `json:"outputs"`
	Error           string             `json:"error"`
}

type executorArtifact struct {
	Index     int    `json:"index"`
	Path      string `json:"path"`
	SizeBytes int64  `json:"sizeBytes"`
}

func decodeFacts(data []byte) (executorFacts, error) {
	var r executorFacts
	d := json.NewDecoder(bytes.NewReader(data))
	d.DisallowUnknownFields()
	if err := d.Decode(&r); err != nil {
		return r, fmt.Errorf("sandbox executor returned invalid facts: %w", err)
	}
	if d.More() {
		return r, fmt.Errorf("sandbox executor returned trailing data")
	}
	known := r.Reason == ""
	for _, reason := range AllReasons() {
		known = known || r.Reason == reason
	}
	if r.Version != 1 || !known || r.CPUNs < 0 || r.MemoryBytes < 0 || r.ClockNs < 0 || r.StdoutBytes < 0 || r.StderrBytes < 0 {
		return r, fmt.Errorf("sandbox executor returned malformed facts")
	}
	return r, nil
}

func (r executorFacts) toFacts() Facts {
	f := Facts{ExitCode: r.ExitCode, Signal: r.Signal, CPUNs: r.CPUNs, MemoryBytes: r.MemoryBytes,
		ClockNs: r.ClockNs, Reason: r.Reason, GroupAccounting: true, OOMKilled: r.OOM > 0 && r.OOMKill > 0}
	if r.Cancelled {
		f.Reason = ReasonCancelled
	}
	if r.OutputExceeded && f.Reason == "" {
		f.Reason = ReasonOutput
	}
	return f
}
