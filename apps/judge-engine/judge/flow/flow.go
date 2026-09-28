package flow

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"slices"
	"strings"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/language"
	"cherry-oj/judge-engine/judge/testcase"
)

// Sandbox 是判题所消费的能力；实现不依赖 flow，测试无需启动内核沙箱。
type Sandbox interface {
	Upload(context.Context, io.Reader) (string, error)
	Run(context.Context, contract.RunSpec) (contract.RunResult, error)
	Delete(context.Context, string) error
}

// Judge 完成一次判题并释放引用；请求及基础设施错误归为 SE。
// logger 记录不影响结论、但需要留痕的问题（如删除 blob 失败）；nil 使用 slog.Default。
func Judge(ctx context.Context, sb Sandbox, cfg config.Settings, req contract.JudgeRequest, logger *slog.Logger) contract.JudgeResult {
	if logger == nil {
		logger = slog.Default()
	}
	req.Cases = slices.Clone(req.Cases)
	job := judgment{sandbox: sb, config: cfg, request: req, log: logger}
	return job.run(ctx)
}

// judgment 只属于一次 Judge 调用。配置为值快照，源码和编译引用不能跨请求复用。
// 测例输入的临时引用由 runCase 在每点结束时释放，不累积到整次判题结束。
type judgment struct {
	sandbox                  Sandbox
	config                   config.Settings
	request                  contract.JudgeRequest
	language                 language.Language
	cases                    []testcase.TestCase
	clockNs                  int64
	sourceRef, executableRef string
	log                      *slog.Logger
}

func (j *judgment) run(ctx context.Context) (result contract.JudgeResult) {
	// 指纹来自实际运行配置；提前 CE/SE 也必须返回，不能回显请求环境。
	defer func() { result.EnvironmentFingerprint = j.config.EnvironmentFingerprint }()
	defer j.close(ctx)
	if err := j.prepare(ctx); err != nil {
		return systemError("%v", err)
	}
	var early *contract.JudgeResult
	j.executableRef, early = j.compile(ctx)
	if early != nil {
		return *early
	}
	result = contract.JudgeResult{Verdict: contract.VerdictAC}
	for i, tc := range j.cases {
		caseResult := j.runCase(ctx, i+1, tc)
		result.CaseResults = append(result.CaseResults, caseResult)
		result.CPUNs = max(result.CPUNs, caseResult.CPUNs)
		result.MemoryBytes = max(result.MemoryBytes, caseResult.MemoryBytes)
		result.Verdict = worse(result.Verdict, caseResult.Verdict)
	}
	result.Score = scoreOf(result.Verdict)
	return result
}

func (j *judgment) prepare(ctx context.Context) error {
	if j.request.Mode == "" {
		j.request.Mode = contract.ModeSubmit
	}
	if !j.request.Mode.IsValid() {
		return fmt.Errorf("invalid judge mode: %q", j.request.Mode)
	}
	if err := j.request.Limits.Validate(); err != nil {
		return fmt.Errorf("invalid judge limits: %v", err)
	}
	var ok bool
	j.language, ok = language.Get(j.request.LanguageID)
	if !ok {
		return fmt.Errorf("unknown language: %q", j.request.LanguageID)
	}
	var err error
	j.cases, err = loadCases(j.config, j.request)
	if err != nil {
		return fmt.Errorf("load test cases: %v", err)
	}
	if len(j.cases) == 0 {
		return fmt.Errorf("no test cases")
	}
	j.clockNs, err = effectiveClockNs(j.request.Limits, j.config.ClockRatio)
	if err != nil {
		return fmt.Errorf("calculate clock limit: %v", err)
	}
	// 请求可以显式指定墙钟，也可以经倍率得到比编译更长的期限；上传前拒绝必然冲突。
	// 这里只检查执行墙钟，排队、回收与传输仍需调用期限留出余量。
	if j.clockNs >= int64(j.config.SandboxTimeout) {
		return fmt.Errorf("judge.sandboxTimeout (%s) must exceed execution clockNs (%d)", j.config.SandboxTimeout, j.clockNs)
	}
	ref, err := j.sandbox.Upload(ctx, strings.NewReader(j.request.Source))
	if err != nil {
		return fmt.Errorf("upload source: %v", err)
	}
	if ref == "" {
		return fmt.Errorf("upload source: sandbox returned an empty ref")
	}
	j.sourceRef = ref
	return nil
}
func (j *judgment) close(ctx context.Context) {
	if j.executableRef != "" {
		j.deleteRef(ctx, j.executableRef)
		j.executableRef = ""
	}
	if j.sourceRef != "" {
		j.deleteRef(ctx, j.sourceRef)
		j.sourceRef = ""
	}
}
func loadCases(cfg config.Settings, req contract.JudgeRequest) ([]testcase.TestCase, error) {
	if req.Mode.UsesVersionedTestdata() {
		return testcase.Load(cfg.TestdataRoot, req.TestDataVersionID)
	}
	return testcase.FromSpecs(req.Cases), nil
}

// deleteRef 用不随请求取消的上下文删除 blob。失败不影响判题结论（sandbox 的 store
// 过了保留期会自行清理），但要留痕：持续失败说明 sandbox 的 store 或连接有问题。
func (j *judgment) deleteRef(ctx context.Context, ref string) {
	if err := j.sandbox.Delete(context.WithoutCancel(ctx), ref); err != nil {
		j.log.Warn("judge.blob.delete.failed", "submissionId", j.request.SubmissionID, "ref", ref, "error", err)
	}
}
