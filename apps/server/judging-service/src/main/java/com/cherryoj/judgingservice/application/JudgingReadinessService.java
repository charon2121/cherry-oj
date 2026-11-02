package com.cherryoj.judgingservice.application;

import com.cherryoj.judgingservice.api.JudgingApiException;
import com.cherryoj.judgingservice.api.JudgingDtos.BenchmarkSummary;
import com.cherryoj.judgingservice.api.JudgingDtos.Calibration;
import com.cherryoj.judgingservice.api.JudgingDtos.CalibrationRequest;
import com.cherryoj.judgingservice.api.JudgingDtos.ExecutionProfile;
import com.cherryoj.judgingservice.api.JudgingDtos.LanguageLimits;
import com.cherryoj.judgingservice.api.JudgingDtos.LanguageLimitsList;
import com.cherryoj.judgingservice.api.JudgingDtos.Readiness;
import com.cherryoj.judgingservice.api.JudgingDtos.ReadinessCheck;
import com.cherryoj.judgingservice.domain.UuidV7;
import com.cherryoj.judgingservice.judge.JudgeGateway;
import com.cherryoj.judgingservice.judge.JudgeGateway.JudgeCallException;
import com.cherryoj.judgingservice.judge.LanguageFiles;
import com.cherryoj.judgingservice.limits.LanguageLimitModels;
import com.cherryoj.judgingservice.limits.LimitModel;
import com.cherryoj.judgingservice.limits.PlatformLimits;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import com.cherryoj.judgingservice.persistence.JudgingRepository;
import com.cherryoj.judgingservice.persistence.JudgingRepository.CalibrationRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class JudgingReadinessService {
    private static final int MAX_SAFE_ERROR = 128;
    private final JudgeNodeRepository nodes;
    private final JudgingRepository repository;
    private final JudgeGateway judge;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final PlatformLimits platform;
    private final LanguageLimitModels models;

    public JudgingReadinessService(
            JudgingRepository repository,
            JudgeGateway judge,
            UuidV7 ids,
            Clock clock,
            ObjectMapper json,
            PlatformTransactionManager transactionManager,
            JudgeNodeRepository nodes,
            PlatformLimits platform,
            LanguageLimitModels models) {
        this.repository = repository;
        this.platform = platform;
        this.models = models;
        this.nodes = nodes;
        this.judge = judge;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Calibration calibrate(CalibrationRequest request, String actorId, String traceId) {
        if (request.referenceSource().getBytes(StandardCharsets.UTF_8).length > 1_048_576) {
            throw new JudgingApiException(HttpStatus.PAYLOAD_TOO_LARGE, "REFERENCE_SOURCE_TOO_LARGE", "参考源码超过安全限额。");
        }
        if ((request.referenceSolutionSource() == null) != (request.solutionPrefixLineCount() == null)
                || (request.referenceSolutionSource() != null && LanguageFiles.of(request.languageId()) == null)) {
            throw new JudgingApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_CALIBRATION_REQUEST",
                    "参考用户文件与前缀行数必须同时给出，且只用于支持核心代码模式的语言。");
        }
        requireAllowedKind(request);
        CalibrationStart start = transactions.execute(status -> startCalibration(request, actorId, traceId));
        String sourceSha = sha256(request.referenceSource().getBytes(StandardCharsets.UTF_8));
        JudgeGateway.JudgeResult result;
        try {
            result = judge.judge(
                    start.endpoint(),
                    new JudgeGateway.JudgeRequest(
                            start.calibrationId(),
                            request.problemId(),
                            request.testDataLocation(),
                            request.languageId(),
                            request.referenceSource(),
                            request.referenceSolutionSource(),
                            new JudgeGateway.Limits(request.cpuNs(), request.memoryBytes(), request.clockNs()),
                            "submit"),
                    traceId);
        } catch (JudgeCallException error) {
            BenchmarkSummary summary = new BenchmarkSummary(sourceSha, "SE", null, null, null);
            finishFailedCalibration(start.calibrationId(), summary, safe(error.getMessage()), actorId, traceId);
            return mapCalibration(repository.findCalibration(start.calibrationId()));
        }

        String verdict = safeVerdict(result.verdict());
        BenchmarkSummary summary = new BenchmarkSummary(
                sourceSha, verdict, nonNegative(result.cpuNs()), nonNegative(result.memoryBytes()), null);
        if (!"AC".equals(verdict)) {
            finishFailedCalibration(start.calibrationId(), summary, "REFERENCE_" + verdict, actorId, traceId);
            return mapCalibration(repository.findCalibration(start.calibrationId()));
        }
        // 判题读到的不是请求里的那份数据：标定中途数据被替换，这次标定对应不上任何一份数据，作废。
        if (!request.testDataDigest().equals(result.testDataDigest())) {
            finishFailedCalibration(start.calibrationId(), summary, "TEST_DATA_CHANGED", actorId, traceId);
            return mapCalibration(repository.findCalibration(start.calibrationId()));
        }

        return transactions.execute(status -> finishValidCalibration(start, request, summary, actorId, traceId));
    }

    /**
     * @param maxTestcaseIoBytes 题目当前测试数据里单个测试点输入加输出的最大字节数，推导限制的 IO 项要用
     */
    public Readiness readiness(String problemId, String languageId, String testDataDigest, long maxTestcaseIoBytes) {
        if (maxTestcaseIoBytes < 0) throw new IllegalArgumentException("maxTestcaseIoBytes must not be negative");
        TransactionTemplate read = new TransactionTemplate(transactions.getTransactionManager());
        read.setReadOnly(true);
        return read.execute(status -> resolveReadiness(problemId, languageId, testDataDigest, maxTestcaseIoBytes));
    }

    private CalibrationStart startCalibration(CalibrationRequest request, String actorId, String traceId) {
        var node = requireOnlineNode(request.languageId());
        String id = ids.next().toString();
        repository.insertCalibration(
                id,
                request.problemId(),
                request.languageId(),
                request.kind(),
                request.testDataDigest(),
                request.cpuNs(),
                request.memoryBytes(),
                request.clockNs(),
                now());
        audit(
                "CALIBRATION",
                id,
                actorId,
                "CALIBRATION_STARTED",
                traceId,
                Map.of(
                        "calibrationId",
                        id,
                        "problemId",
                        request.problemId(),
                        "testDataDigest",
                        request.testDataDigest(),
                        "nodeId",
                        node.nodeId(),
                        "languageId",
                        request.languageId(),
                        "kind",
                        request.kind(),
                        "cpuNs",
                        request.cpuNs(),
                        "memoryBytes",
                        request.memoryBytes()));
        return new CalibrationStart(id, node.endpoint());
    }

    private Calibration finishValidCalibration(
            CalibrationStart start,
            CalibrationRequest request,
            BenchmarkSummary summary,
            String actorId,
            String traceId) {
        requireOnlineNode(request.languageId());
        // 同一题目 × 语言只留一条 VALID；新的基础标定还要替掉旧的基础标定（可能是另一门语言）。
        CalibrationRow sameLanguage = repository.findValid(request.problemId(), request.languageId(), true);
        CalibrationRow previousBase = repository.findValidBase(request.problemId(), true);
        if ("OVERRIDE".equals(request.kind()) && isCurrentBase(previousBase, request)) {
            throw conflict("OVERRIDE_ON_BASE_LANGUAGE", "该语言是题目的基础语言，请改做基础标定。");
        }
        if (!"BASE".equals(request.kind())) previousBase = null;
        for (CalibrationRow previous : distinct(sameLanguage, previousBase)) {
            if (repository.supersede(previous.id(), now(), previous.rowVersion()) != 1) {
                throw conflict("CALIBRATION_STATE_CONFLICT", "有效校准已经改变，请重试。");
            }
        }
        String previousId = previousBase != null ? previousBase.id() : sameLanguage == null ? null : sameLanguage.id();
        if (repository.markCalibrationValid(start.calibrationId(), actorId, previousId, writeJson(summary), now())
                != 1) {
            throw conflict("CALIBRATION_STATE_CONFLICT", "校准状态已经改变，请重试。");
        }
        audit(
                "CALIBRATION",
                start.calibrationId(),
                actorId,
                "CALIBRATION_VALIDATED",
                traceId,
                Map.of(
                        "calibrationId",
                        start.calibrationId(),
                        "problemId",
                        request.problemId(),
                        "languageId",
                        request.languageId(),
                        "sourceSha256",
                        summary.sourceSha256(),
                        "verdict",
                        summary.verdict()));
        return mapCalibration(repository.findCalibration(start.calibrationId()));
    }

    private void finishFailedCalibration(
            String id, BenchmarkSummary summary, String error, String actorId, String traceId) {
        transactions.executeWithoutResult(status -> {
            if (repository.markCalibrationFailed(id, writeJson(summary), safe(error), now()) != 1) {
                throw conflict("CALIBRATION_STATE_CONFLICT", "校准状态已经改变，请重试。");
            }
            audit(
                    "CALIBRATION",
                    id,
                    actorId,
                    "CALIBRATION_FAILED",
                    traceId,
                    Map.of(
                            "calibrationId",
                            id,
                            "sourceSha256",
                            summary.sourceSha256(),
                            "verdict",
                            summary.verdict(),
                            "failureCode",
                            safe(error)));
        });
    }

    /**
     * docs/protocols/limits.md 第 7 节：基础语言必须可作基础；手填的限制受平台上限约束，和推导值一样；
     * 覆盖标定不能用于当前基础语言。完成时还会在锁内再核对一次最后这条。
     */
    private void requireAllowedKind(CalibrationRequest request) {
        if ("BASE".equals(request.kind())) {
            LimitModel model = models.of(request.languageId());
            if (model == null || !model.baseEligible()) {
                throw new JudgingApiException(HttpStatus.BAD_REQUEST, "LANGUAGE_NOT_BASE_ELIGIBLE", "该语言不能作为基础语言。");
            }
        }
        if (request.cpuNs() > platform.maxCpuNs() || request.memoryBytes() > platform.maxMemoryBytes()) {
            throw new JudgingApiException(HttpStatus.BAD_REQUEST, "LIMIT_ABOVE_PLATFORM", "限制超过平台上限。");
        }
        if ("OVERRIDE".equals(request.kind())
                && isCurrentBase(repository.findValidBase(request.problemId(), false), request)) {
            throw conflict("OVERRIDE_ON_BASE_LANGUAGE", "该语言是题目的基础语言，请改做基础标定。");
        }
    }

    /** 对应当前这份测试数据、且是同一门语言的基础标定；对应旧数据的基础标定已经过期，不挡覆盖标定。 */
    private static boolean isCurrentBase(CalibrationRow base, CalibrationRequest request) {
        return base != null
                && base.languageId().equals(request.languageId())
                && base.testDataDigest().equals(request.testDataDigest());
    }

    private static java.util.List<CalibrationRow> distinct(CalibrationRow first, CalibrationRow second) {
        ArrayList<CalibrationRow> rows = new ArrayList<>();
        if (first != null) rows.add(first);
        if (second != null && (first == null || !first.id().equals(second.id()))) rows.add(second);
        return rows;
    }

    /**
     * 题目当前的测试数据指纹 = 标定时的指纹，旧标定才算数；数据一换，标定即过期。
     * 限制按 docs/protocols/limits.md 第 2 节取第一个可用的：该语言的覆盖标定 → 基础标定本身 → 由基础标定推导。
     */
    private Readiness resolveReadiness(
            String problemId, String languageId, String testDataDigest, long maxTestcaseIoBytes) {
        ArrayList<ReadinessCheck> checks = new ArrayList<>();
        boolean online = !nodes.online(now()).isEmpty();
        checks.add(check("ONLINE_JUDGE_NODE", online, online ? "在线判题节点可用。" : "当前没有在线判题节点，请启动节点并等待注册。"));
        boolean language = !nodes.online(languageId, now()).isEmpty();
        checks.add(check("LANGUAGE", language, language ? "有在线节点支持该语言。" : "没有在线节点支持该语言。"));

        Resolution r = inspect(problemId, languageId, testDataDigest);
        checks.add(check(
                "CALIBRATION",
                r.calibrated(),
                r.override() != null
                        ? "该语言有对应当前测试数据的覆盖标定。"
                        : r.baseCurrent()
                                ? "题目有对应当前测试数据的基础标定。"
                                : r.base() == null && r.own() == null ? "题目缺少基础标定。" : "测试数据已更新，标定已过期，请重新标定。"));
        checks.add(check(
                "LIMIT_MODEL",
                r.modelPassed(),
                r.override() != null
                        ? "该语言使用覆盖标定，不需要推导。"
                        : r.isBaseLanguage()
                                ? "该语言是基础语言，不需要推导。"
                                : r.target() == null
                                        ? "该语言没有限制参数，只能做覆盖标定。"
                                        : r.modelPassed() ? "该语言有限制参数，可以由基础标定推导。" : "基础语言的限制参数已移除，请重新做基础标定。"));

        boolean ready = checks.stream().allMatch(ReadinessCheck::passed);
        return new Readiness(ready, checks, ready ? profile(r, maxTestcaseIoBytes) : null);
    }

    /**
     * 管理端标定页用：每门语言的限制及其来源，顺序与 languageIds 一致。不看在线节点，只回答"限制是多少、从哪来"，
     * 规则与就绪判定共用 {@link #inspect}。languageIds 是 problem-service 给出的题目开放语言。
     */
    public LanguageLimitsList limits(
            String problemId, List<String> languageIds, String testDataDigest, long maxTestcaseIoBytes) {
        if (maxTestcaseIoBytes < 0) throw new IllegalArgumentException("maxTestcaseIoBytes must not be negative");
        TransactionTemplate read = new TransactionTemplate(transactions.getTransactionManager());
        read.setReadOnly(true);
        return read.execute(status -> new LanguageLimitsList(languageIds.stream()
                .map(languageId ->
                        languageLimits(languageId, inspect(problemId, languageId, testDataDigest), maxTestcaseIoBytes))
                .toList()));
    }

    private LanguageLimits languageLimits(String languageId, Resolution r, long io) {
        String reason = !r.calibrated()
                ? "NO_CALIBRATION"
                : r.modelPassed() ? null : r.target() == null ? "NO_LIMIT_MODEL" : "BASE_LIMIT_MODEL_REMOVED";
        if (reason != null) return new LanguageLimits(languageId, null, null, null, null, null, false, false, reason);
        ExecutionProfile profile = profile(r, io);
        var derivation = profile.limitDerivation();
        return new LanguageLimits(
                languageId,
                profile.limitSource(),
                profile.cpuNs(),
                profile.memoryBytes(),
                profile.clockNs(),
                profile.calibrationId(),
                derivation != null && derivation.cpuCapped(),
                derivation != null && derivation.memoryCapped(),
                null);
    }

    /** 解析一门语言的限制所需的全部事实；对应旧数据的标定一律按过期处理。 */
    private Resolution inspect(String problemId, String languageId, String testDataDigest) {
        CalibrationRow own = repository.findValid(problemId, languageId, false);
        CalibrationRow override =
                own != null && "OVERRIDE".equals(own.kind()) && testDataDigest.equals(own.testDataDigest())
                        ? own
                        : null;
        CalibrationRow base = repository.findValidBase(problemId, false);
        boolean baseCurrent = base != null && testDataDigest.equals(base.testDataDigest());
        LimitModel baseModel = base == null ? null : models.of(base.languageId());
        return new Resolution(own, override, base, baseCurrent, languageId, models.of(languageId), baseModel);
    }

    /** 调用前要求 calibrated() 与 modelPassed() 都成立。 */
    private ExecutionProfile profile(Resolution r, long io) {
        if (r.override() != null) return calibrated(r.override());
        if (r.isBaseLanguage()) return calibrated(r.base());
        return derived(r.base(), r.baseModel(), r.target(), io);
    }

    private record Resolution(
            CalibrationRow own,
            CalibrationRow override,
            CalibrationRow base,
            boolean baseCurrent,
            String languageId,
            LimitModel target,
            LimitModel baseModel) {
        boolean calibrated() {
            return override != null || baseCurrent;
        }

        boolean isBaseLanguage() {
            return base != null && base.languageId().equals(languageId);
        }

        boolean modelPassed() {
            return override != null || isBaseLanguage() || (target != null && (base == null || baseModel != null));
        }
    }

    /** BASE、OVERRIDE：标定里的绝对值原样生效。 */
    private static ExecutionProfile calibrated(CalibrationRow row) {
        return new ExecutionProfile(
                row.id(), row.cpuNs(), row.memoryBytes(), row.clockNs(), row.kind(), null, row.approvedAt());
    }

    /** DERIVED：不带 clockNs，墙钟由 judge 按 cpuNs 推算；推导依据随提交冻结。 */
    private ExecutionProfile derived(CalibrationRow base, LimitModel baseModel, LimitModel target, long io) {
        var result = com.cherryoj.judgingservice.limits.LimitDerivation.derive(
                baseModel,
                base.cpuNs(),
                base.memoryBytes(),
                target,
                io,
                platform.maxCpuNs(),
                platform.maxMemoryBytes());
        var derivation = new com.cherryoj.judgingservice.api.JudgingDtos.LimitDerivation(
                base.languageId(),
                base.id(),
                base.cpuNs(),
                base.memoryBytes(),
                io,
                result.refCpuNs(),
                result.refMemoryBytes(),
                platform.maxCpuNs(),
                platform.maxMemoryBytes(),
                result.cpuCapped(),
                result.memoryCapped(),
                snapshot(baseModel),
                snapshot(target));
        return new ExecutionProfile(
                base.id(), result.cpuNs(), result.memoryBytes(), null, "DERIVED", derivation, base.approvedAt());
    }

    private static com.cherryoj.judgingservice.api.JudgingDtos.LimitModel snapshot(LimitModel model) {
        return new com.cherryoj.judgingservice.api.JudgingDtos.LimitModel(
                model.cpuFactorPermille(),
                model.cpuFactorCapPermille(),
                model.cpuOverheadNs(),
                model.ioPicosPerByte(),
                model.memoryFactorPermille(),
                model.memoryOverheadBytes());
    }

    /** 任何在线且声明了该语言的节点都行：它们按同一个地址读同一份测试数据。 */
    private JudgeNodeRepository.Node requireOnlineNode(String languageId) {
        var online = nodes.online(languageId, now());
        if (online.isEmpty()) throw noOnline();
        return online.getFirst();
    }

    public static JudgingApiException noOnline() {
        return new JudgingApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "NO_ONLINE_JUDGE_NODE", "当前没有在线判题节点，请启动节点并等待注册后重试。");
    }

    private void audit(
            String type,
            String aggregateId,
            String actorId,
            String action,
            String traceId,
            Map<String, Object> detail) {
        repository.insertAudit(
                ids.next().toString(),
                type,
                aggregateId,
                actorId,
                action,
                safeTrace(traceId),
                writeJson(detail),
                now());
    }

    private Calibration mapCalibration(CalibrationRow row) {
        BenchmarkSummary summary = row.benchmarkSummaryJson() == null
                ? null
                : json.readValue(row.benchmarkSummaryJson(), BenchmarkSummary.class);
        return new Calibration(
                row.id(),
                row.problemId(),
                row.languageId(),
                row.kind(),
                row.status(),
                row.cpuNs(),
                row.memoryBytes(),
                row.clockNs(),
                row.testDataDigest(),
                summary,
                row.errorMessage(),
                row.createdAt(),
                row.updatedAt(),
                row.rowVersion());
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (RuntimeException error) {
            throw new IllegalStateException("could not serialize safe metadata", error);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Long nonNegative(Long value) {
        return value == null || value < 0 ? null : value;
    }

    private static String safeVerdict(String verdict) {
        return verdict != null
                        && java.util.Set.of("AC", "WA", "TLE", "MLE", "RE", "CE", "SE")
                                .contains(verdict)
                ? verdict
                : "SE";
    }

    private static String safe(String error) {
        String value = error == null || error.isBlank() ? "UNKNOWN_FAILURE" : error;
        return value.substring(0, Math.min(value.length(), MAX_SAFE_ERROR));
    }

    private static String safeTrace(String trace) {
        if (trace == null || trace.isBlank()) return null;
        return trace.substring(0, Math.min(trace.length(), 128));
    }

    private static ReadinessCheck check(String code, boolean passed, String message) {
        return new ReadinessCheck(code, passed, message);
    }

    private static JudgingApiException conflict(String code, String message) {
        return new JudgingApiException(HttpStatus.CONFLICT, code, message);
    }

    private record CalibrationStart(String calibrationId, String endpoint) {}
}
