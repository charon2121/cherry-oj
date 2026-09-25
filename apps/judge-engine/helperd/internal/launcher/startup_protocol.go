//go:build linux && amd64

package launcher

import "cherry-oj/judge-engine/internal/hostexec"

// os/exec 将 ExtraFiles[i] 映射到子进程 FD 3+i；0/1/2 是标准输入、输出、错误。
// 两组 FD 属于不同进程，不能因为编号相同而复用角色名称。
const (
	ExtraFilesBaseFD = 3

	// helper.execution.prepare → Dispatch(--isolated-init)/initSession.run。
	InitControlFD  = 3 // seqpacket：workspace FD、ready/exit 事件与 helper 的 GO。
	InitInputFD    = 4 // StageSpec 控制帧，随后是输入文件与 stdin 字节流。
	InitLivenessFD = 5 // helper 持有写端；断连使 namespace PID 1 退出。

	// initSession.startPayload → Dispatch(--isolated-exec)/RunExecStage。
	ExecConfigFD = 3 // ExecSpec 控制帧。
	ExecReadyFD  = 4 // exec 写 READY，init 在收到 helper 放行后转发 GO。
	ExecErrorFD  = 5 // 最终 exec 失败记录；成功 exec 时由 CLOEXEC 关闭。
)

// READY/GO 是单字节握手，不是 JSON Event：
// exec READY → init ready Event → helper GO → init GO → execve。
const (
	PayloadReady   byte = 'R'
	PayloadGo      byte = 'G'
	handshakeBytes      = 1

	// init 安装过滤器后直接写此消息，不能换成现场 JSON 编码。
	// 协议测试核对其 Version/Kind 与 Event，保持现有线格式。
	initReadyMessage = `{"Version":1,"Kind":"ready"}`
)

// execFailureStage 是固定线协议；编号显式保留，不能按执行顺序重新排列。
// 握手发生在 execve 前，但历史编号仍是 7。
type execFailureStage byte

const (
	execFailureConfig      execFailureStage = 1
	execFailureRlimit      execFailureStage = 2
	execFailureCloseOnExec execFailureStage = 3
	execFailurePrivileges  execFailureStage = 4
	execFailureSeccomp     execFailureStage = 5
	execFailureExecve      execFailureStage = 6
	execFailureHandshake   execFailureStage = 7
)

// terminalFailure → initSession.reportExit：stage 占第 0 字节，1～3 保留为零，
// errno 占第 4～7 字节（uint32 小端）。该管道成功 exec 后只读到 EOF。
const (
	execFailureRecordBytes  = 8
	execFailureStageOffset  = 0
	execFailureErrnoOffset  = 4
	launcherFailureExitCode = 125 // 可信启动器自身失败；不覆盖 payload 的实际退出码。
)

// StageSpec 只通过 helper 创建的匿名管道传给可信 init，绝不从 socket 客户端解码。
type StageSpec struct {
	Request                                  hostexec.Request
	RootFS, MountPoint, Executable           string
	PayloadUID, PayloadGID, InitUID, InitGID int
	WorkspaceBytes                           int64
	WorkspaceInodes                          int
}

// Event 经 seqpacket 保留消息边界；workspace 事件可附一个目录 FD。
// exit 只报告 payload 退出，helper 仍须停止整组并等待 init/I/O，不能据此发布结果。
type Event struct {
	Errno            uint32 // 可信 init 失败的原始 errno；最终 exec 使用 ExecErrno。
	Phase            string
	ExecStage        byte
	ExecErrno        uint32
	Version          int
	Kind             string
	ExitCode, Signal int
	ExecFailed       bool
}
