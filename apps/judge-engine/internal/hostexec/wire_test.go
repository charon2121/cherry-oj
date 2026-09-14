package hostexec_test

import (
	"encoding/json"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/internal/hostexec"
)

// 这两个串是线格式本身。Result 与 Request 没有 JSON 标注，字段名即协议，
// 改名、调序或增删字段都会改变它——客户端与 helper 按同一构建批次交付，
// 但仍不允许在一次重构里无意改掉线格式。要改协议先改 contracts 与版本号。
const (
	goldenResult  = `{"Cancelled":false,"OutputExceeded":true,"Version":1,"ExitCode":3,"Signal":9,"Usage":{"CPUNs":1,"MemoryBytes":2,"OOM":3,"OOMKill":4,"MemoryMaxEvents":5,"PidsMaxEvents":6,"Populated":true},"ClockNs":42,"Reason":"cpu","Stdout":"YQ==","Stderr":null,"Outputs":[{"Path":"main","SizeBytes":7}],"Error":"boom"}`
	goldenRequest = `{"Version":1,"Command":["main"],"Env":["PATH=/usr/bin"],"Inputs":[{"Path":"main.cpp","SizeBytes":3,"Executable":false}],"StdinBytes":2,"Outputs":["main"],"Limits":{"clockNs":2000000000,"cpuNs":1000000000,"maxProcesses":32,"memoryBytes":67108864,"stderrMaxBytes":1024,"stdoutMaxBytes":1024}}`
)

func TestResultWireFormat(t *testing.T) {
	b, err := json.Marshal(hostexec.Result{
		OutputExceeded: true, Version: 1, ExitCode: 3, Signal: 9, ClockNs: 42,
		Reason: hostexec.ReasonCPU, Stdout: []byte("a"), Error: "boom",
		Usage: hostexec.Usage{
			CPUNs: 1, MemoryBytes: 2, OOM: 3, OOMKill: 4,
			MemoryMaxEvents: 5, PidsMaxEvents: 6, Populated: true,
		},
		Outputs: []hostexec.Output{{Path: "main", SizeBytes: 7}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if string(b) != goldenResult {
		t.Fatalf("Result 线格式已改变\n实际: %s\n期望: %s", b, goldenResult)
	}
}

func TestRequestWireFormat(t *testing.T) {
	b, err := json.Marshal(hostexec.Request{
		Version: 1, Command: []string{"main"}, Env: []string{"PATH=/usr/bin"},
		Inputs: []hostexec.Input{{Path: "main.cpp", SizeBytes: 3}}, StdinBytes: 2,
		Outputs: []string{"main"},
		Limits: contract.ExplicitLimits(contract.Limits{
			CPUNs: 1e9, ClockNs: 2e9, MemoryBytes: 64 << 20,
			MaxProcesses: 32, StdoutMaxBytes: 1024, StderrMaxBytes: 1024,
		}),
	})
	if err != nil {
		t.Fatal(err)
	}
	if string(b) != goldenRequest {
		t.Fatalf("Request 线格式已改变\n实际: %s\n期望: %s", b, goldenRequest)
	}
}

// Completion 是交付确认尾帧；缺它不能认定完整交付，因此它的线格式同样固定。
func TestCompletionWireFormat(t *testing.T) {
	b, err := json.Marshal(hostexec.Completion{Version: hostexec.Version, Complete: true})
	if err != nil {
		t.Fatal(err)
	}
	if want := `{"Version":1,"Complete":true}`; string(b) != want {
		t.Fatalf("Completion 线格式已改变\n实际: %s\n期望: %s", b, want)
	}
}
