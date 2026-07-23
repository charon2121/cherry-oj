package contract

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
