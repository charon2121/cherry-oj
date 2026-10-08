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
	req.Testcases = slices.Clone(req.Testcases)
	job := judgment{sandbox: sb, config: cfg, request: req, log: logger}
	return job.run(ctx)
}

// judgment 只属于一次 Judge 调用。配置为值快照，源码和编译引用不能跨请求复用。
// 测试点输入的临时引用由 runTestcase 在每点结束时释放，不累积到整次判题结束；
// submit 模式下测试数据的本地副本（testData）随这次判题一起在 close 里删除。
type judgment struct {
	sandbox                  Sandbox
	config                   config.Settings
	request                  contract.JudgeRequest
	language                 language.Language
	testData                 testcase.Set
	testcases                []testcase.TestCase
	clockNs                  int64
	sourceRef, executableRef string
	log                      *slog.Logger
}

func (j *judgment) run(ctx context.Context) contract.JudgeResult {
	defer j.close(ctx)
	if err := j.prepare(ctx); err != nil {
		return systemError("%v", err)
	}
	result := j.judge(ctx)
	result.TestDataDigest = j.testData.Digest
	return result
}

// judge 编译并逐点运行；调用前 prepare 已经成功，测试数据已经就绪。
func (j *judgment) judge(ctx context.Context) contract.JudgeResult {
	var early *contract.JudgeResult
	j.executableRef, early = j.compile(ctx)
	if early != nil {
		return *early
	}
	result := contract.JudgeResult{Verdict: contract.VerdictAC}
	for i, tc := range j.testcases {
		testcaseResult := j.runTestcase(ctx, i+1, tc)
		result.TestcaseResults = append(result.TestcaseResults, testcaseResult)
		result.CPUNs = max(result.CPUNs, testcaseResult.CPUNs)
		result.MemoryBytes = max(result.MemoryBytes, testcaseResult.MemoryBytes)
		result.Verdict = worse(result.Verdict, testcaseResult.Verdict)
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
	j.testData, err = loadTestcases(ctx, j.config, j.request)
	if err != nil {
		return fmt.Errorf("load testcases: %v", err)
	}
	j.testcases = j.testData.Testcases
	if len(j.testcases) == 0 {
		return fmt.Errorf("no testcases")
	}
	j.clockNs, err = effectiveClockNs(j.request.Limits, j.config.ClockRatio)
	if err != nil {
		return fmt.Errorf("calculate clock limit: %v", err)
	}
	// 请求可以显式指定墙钟，也可以经倍率得到更长的期限；超过执行层硬界的执行必然被拒绝，
	// 上传源码前就说清楚原因。
	if j.clockNs > config.MaxClockNs {
		return fmt.Errorf("execution clockNs (%d) exceeds the wall-clock hard limit (%d)", j.clockNs, config.MaxClockNs)
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
	// 副本删不掉不影响结论，但持续失败会把磁盘占满，要留痕。
	if err := j.testData.Close(); err != nil {
		j.log.Warn("judge.testdata.cleanup.failed", "submissionId", j.request.SubmissionID, "error", err)
	}
	j.testData = testcase.Set{}
	if j.executableRef != "" {
		j.deleteRef(ctx, j.executableRef)
		j.executableRef = ""
	}
	if j.sourceRef != "" {
		j.deleteRef(ctx, j.sourceRef)
		j.sourceRef = ""
	}
}
func loadTestcases(ctx context.Context, cfg config.Settings, req contract.JudgeRequest) (testcase.Set, error) {
	if req.Mode.UsesTestData() {
		return testcase.Load(ctx, testcase.Options{
			WorkRoot:      cfg.Testdata.WorkRoot,
			MaxFileBytes:  cfg.Testdata.MaxFileBytes,
			MaxTotalBytes: cfg.Testdata.MaxTotalBytes,
			FetchTimeout:  cfg.Testdata.FetchTimeout.Std(),
		}, req.TestDataLocation)
	}
	return testcase.Set{Testcases: testcase.FromSpecs(req.Testcases)}, nil
}

// deleteRef 用不随请求取消的上下文删除 blob。失败不影响判题结论（sandbox 的 store
// 过了保留期会自行清理），但要留痕：持续失败说明 sandbox 的 store 或连接有问题。
func (j *judgment) deleteRef(ctx context.Context, ref string) {
	if err := j.sandbox.Delete(context.WithoutCancel(ctx), ref); err != nil {
		j.log.Warn("judge.blob.delete.failed", "submissionId", j.request.SubmissionID, "ref", ref, "error", err)
	}
}
