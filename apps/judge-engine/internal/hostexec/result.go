package hostexec

import "time"

// Call 与 serveConn 使用相同的响应帧预算。产物本体另走受限字节流；
// 结果帧容纳 stdout/stderr 的 JSON 编码和资源事实，完成帧只承载确认。
// 这些是帧分配上限，不改变请求声明的 stdout/stderr 和文件总量限制。
const (
	MaxResultFrameBytes     = 4 << 20
	MaxCompletionFrameBytes = 1024
)

// 双方计时起点不同：Call 拨号后设置，serveConn 校验
// 请求头后设置，覆盖输入、执行、回收和交付。服务端交付时另刷新写端期限，
// 客户端仍受原期限约束，直到 Completion 后的正常 EOF；不是统一绝对截止时间。
const SessionTimeout = 150 * time.Second

// Completion 仅在执行回收及产物 FD 关闭成功后发送；缺失时不能接受部分交付。
// 它不确认连接槽位已归还，Call 还必须等 serveInSlot 关闭连接产生正常 EOF。
type Completion struct {
	Version  int
	Complete bool
}

// Output 的长度划分 Result 后的产物字节流；接收端不得超出该长度读取下一份文件。
type Output struct {
	Path      string
	SizeBytes int64
}

// Usage 是整组累计 CPU 与内存峰值的线格式。特权侧从 cgroup 读到的快照在交付前
// 转换成本类型；字段名即线格式，改名等于改协议。
type Usage struct {
	CPUNs           int64
	MemoryBytes     int64
	OOM             uint64 // 本任务发生 OOM；与仅说明有受害进程的 OOMKill 分开。
	OOMKill         uint64
	MemoryMaxEvents uint64
	PidsMaxEvents   uint64
	Populated       bool
}

// Result 保存执行事实；Error 或回收失败不能被 ExitCode=0 覆盖。
// Outputs 只描述后续文件流，完整性还要由接收端逐个验长并确认 Completion。
type Result struct {
	Cancelled        bool
	OutputExceeded   bool
	Version          int
	ExitCode, Signal int
	Usage            Usage
	ClockNs          int64
	Reason           Reason // 主动终止原因与 Signal 分开记录；不能从 SIGKILL 反推超时或 OOM。
	Stdout, Stderr   []byte
	Outputs          []Output
	Error            string
}

// Reason 是主动终止或平台故障原因，不替代退出信号和 cgroup 资源事实。
// 空值表示没有发生主动终止，由退出码与信号说明结果。
type Reason string

const (
	ReasonCPU       Reason = "cpu"
	ReasonWall      Reason = "wall"
	ReasonOutput    Reason = "output"
	ReasonCancelled Reason = "cancelled"
	ReasonPlatform  Reason = "platform"
)

// AllReasons 列出全部非空取值。消费方的结论映射必须覆盖其中每一项，由测试断言；
// 新增取值而不更新映射时测试失败，避免新原因悄悄落进兜底分支。
func AllReasons() []Reason {
	return []Reason{ReasonCPU, ReasonWall, ReasonOutput, ReasonCancelled, ReasonPlatform}
}
