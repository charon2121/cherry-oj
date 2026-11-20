package com.cherryoj.judgingservice.application;

import com.cherryoj.judgingservice.api.JudgingDtos.ExecutionProfile;
import com.cherryoj.judgingservice.api.JudgingDtos.LanguageLimits;
import com.cherryoj.judgingservice.api.JudgingDtos.LanguageLimitsList;
import com.cherryoj.judgingservice.api.JudgingDtos.Readiness;
import com.cherryoj.judgingservice.api.JudgingDtos.ReadinessCheck;
import com.cherryoj.judgingservice.judge.LanguageCatalog;
import com.cherryoj.judgingservice.limits.LimitModel;
import com.cherryoj.judgingservice.limits.PlatformLimits;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import com.cherryoj.judgingservice.persistence.JudgingRepository;
import com.cherryoj.judgingservice.persistence.JudgingRepository.CalibrationRow;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class JudgingReadinessService {
    private final JudgeNodeRepository nodes;
    private final JudgingRepository repository;
    private final Clock clock;
    private final TransactionTemplate readTransactions;
    private final PlatformLimits platform;
    private final LanguageCatalog languages;

    public JudgingReadinessService(
            JudgingRepository repository,
            Clock clock,
            PlatformTransactionManager transactionManager,
            JudgeNodeRepository nodes,
            PlatformLimits platform,
            LanguageCatalog languages) {
        this.repository = repository;
        this.platform = platform;
        this.languages = languages;
        this.nodes = nodes;
        this.clock = clock;
        this.readTransactions = new TransactionTemplate(transactionManager);
        this.readTransactions.setReadOnly(true);
    }

    /**
     * @param maxTestcaseIoBytes 题目当前测试数据里单个测试点输入加输出的最大字节数，推导限制的 IO 项要用
     */
    public Readiness readiness(String problemId, String languageId, String testDataDigest, long maxTestcaseIoBytes) {
        if (maxTestcaseIoBytes < 0) throw new IllegalArgumentException("maxTestcaseIoBytes must not be negative");
        return readTransactions.execute(
                status -> resolveReadiness(problemId, languageId, testDataDigest, maxTestcaseIoBytes));
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
        return readTransactions.execute(status -> new LanguageLimitsList(languageIds.stream()
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
        LimitModel baseModel = base == null ? null : languages.limitModel(base.languageId());
        return new Resolution(
                own, override, base, baseCurrent, languageId, languages.limitModel(languageId), baseModel);
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

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static ReadinessCheck check(String code, boolean passed, String message) {
        return new ReadinessCheck(code, passed, message);
    }
}
