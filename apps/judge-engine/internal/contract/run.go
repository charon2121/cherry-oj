package contract

import (
	"encoding/json"
	"fmt"
	"strings"
)

type FileSource struct {
	Ref  string `json:"ref,omitempty"`  // store 引用
	Text string `json:"text,omitempty"` // 内联文本，二选一
}

type Limits struct {
	CPUNs          int64 `json:"cpuNs"`
	ClockNs        int64 `json:"clockNs"` // 墙钟；防 sleep
	MemoryBytes    int64 `json:"memoryBytes"`
	MaxProcesses   int   `json:"maxProcesses,omitempty"`
	StdoutMaxBytes int64 `json:"stdoutMaxBytes"`
	StderrMaxBytes int64 `json:"stderrMaxBytes"`
}

type RunSpec struct {
	Command   []string              `json:"command"`
	Env       []string              `json:"env,omitempty"`
	Stdin     *FileSource           `json:"stdin,omitempty"`
	Inputs    map[string]FileSource `json:"inputs,omitempty"`    // 写入工作目录
	Outputs   []string              `json:"outputs,omitempty"`   // 内联取回
	Artifacts []string              `json:"artifacts,omitempty"` // 存入 store
	Limits    Limits                `json:"limits"`
}

type Status string

const (
	StatusOK                  Status = "OK"
	StatusTimeLimitExceeded   Status = "TimeLimitExceeded"
	StatusMemoryLimitExceeded Status = "MemoryLimitExceeded"
	StatusOutputLimitExceeded Status = "OutputLimitExceeded"
	StatusNonzeroExit         Status = "NonzeroExitStatus"
	StatusSignalled           Status = "Signalled"
	StatusWorkspaceError      Status = "WorkspaceError"
	StatusInternalError       Status = "InternalError"
)

type RunResult struct {
	Status      Status            `json:"status"`
	ExitCode    int               `json:"exitCode"`
	Signal      int               `json:"signal"`
	CPUNs       int64             `json:"cpuNs"`
	ClockNs     int64             `json:"clockNs"`
	MemoryBytes int64             `json:"memoryBytes"`
	Stdout      string            `json:"stdout"`
	Stderr      string            `json:"stderr"`
	Outputs     map[string]string `json:"outputs,omitempty"`   // path → 内容
	Artifacts   map[string]string `json:"artifacts,omitempty"` // path → ref
	Error       string            `json:"error,omitempty"`
}

func (f *FileSource) UnmarshalJSON(data []byte) error {
	// json 把 null 解成空串且 err==nil，先挡掉
	if s := strings.TrimSpace(string(data)); s == "" || s == "null" {
		return fmt.Errorf("stdin/inputs 必须是字符串或 {ref|text} 对象")
	}

	// 形态 1：裸字符串
	var s string
	if err := json.Unmarshal(data, &s); err == nil {
		*f = FileSource{Text: s}
		return nil
	}

	// 形态 2：对象
	// 如果直接写 json.Unmarshal(data, f)，json 看到 *FileSource 有 UnmarshalJSON 方法 → 调它
	//  → 里面又 json.Unmarshal(data, f) → 无限递归，栈溢出。
	// Go 的 defined type 不带走原类型的方法集
	// type alias FileSource 定义了一个新类型，它的字段布局和 FileSource 一模一样，但不继承方法（Go 的 defined type 不带走原类型的方法集）。
	// 于是 json 对 alias 只能走默认规则，递归就断了。
	// 最后 FileSource(a) 是一次类型转换——布局相同所以可以直接转。
	// 注意它和 type alias = T（真正的类型别名，带等号）不是一回事——带等号的完全等价、方法也一起带走，那样照样会递归。
	type alias FileSource
	var a alias
	if err := json.Unmarshal(data, &a); err != nil {
		return fmt.Errorf("stdin/inputs 必须是字符串或 {ref|text} 对象: %w", err)
	}
	if (a.Ref == "") == (a.Text == "") {
		// 都空（{} / 未知字段）或都有 → 不是合法的二选一
		return fmt.Errorf("stdin/inputs 对象必须且只能提供 ref 或 text 之一")
	}
	*f = FileSource(a)
	return nil
}
