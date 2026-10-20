package com.cherryoj.problemservice.application;

import com.cherryoj.identity.token.CallUser;
import com.cherryoj.problemservice.api.AdminProblemDtos;
import com.cherryoj.problemservice.api.AdminProblemDtos.CalibrateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.CalibrationStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.JudgeVerdict;
import com.cherryoj.problemservice.api.AdminProblemDtos.Problem;
import com.cherryoj.problemservice.api.AdminProblemDtos.PublishCheck;
import com.cherryoj.problemservice.api.AdminProblemDtos.PublishCheckCode;
import com.cherryoj.problemservice.api.AdminProblemDtos.PublishCheckItem;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.domain.UuidV7;
import com.cherryoj.problemservice.integration.judging.JudgingClient;
import com.cherryoj.problemservice.integration.judging.JudgingDtos;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguagePrefixRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguageRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.SampleRow;
import com.cherryoj.problemservice.storage.TestDataStore;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 标定与公开。题目没有草稿、验证、评审这些状态：公开就是把可见性改成 PUBLIC，前提是检查全部通过；
 * 标定记录在 judging-service，它记下标定时的测试数据指纹，数据一更新旧标定就过期。
 */
@Service
public class ProblemPublicationService {
    private final AdminProblemMapper problems;
    private final TestDataStore testData;
    private final AdminProblemService adminProblems;
    private final JudgingClient judging;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    public ProblemPublicationService(
            AdminProblemMapper problems,
            TestDataStore testData,
            AdminProblemService adminProblems,
            JudgingClient judging,
            UuidV7 ids,
            Clock clock,
            ObjectMapper json,
            PlatformTransactionManager transactionManager) {
        this.problems = problems;
        this.testData = testData;
        this.adminProblems = adminProblems;
        this.judging = judging;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public AdminProblemDtos.LanguageCalibration calibrate(
            String problemId, CalibrateProblemRequest request, CallUser admin, String traceparent, String actorUserId) {
        Snapshot local = transactions.execute(status -> snapshot(problemId, false));
        requireActive(local.problem());
        if (localChecks(local).stream().anyMatch(value -> !value.passed())) {
            throw state("题面、样例、语言或测试数据尚未就绪。");
        }
        JudgingDtos.Calibration result =
                judging.calibrate(calibrationRequest(problemId, request, local), admin, traceparent);
        CalibrationStatus remoteStatus = calibrationStatus(result.status());
        if (!problemId.equals(result.problemId())
                || !request.languageId().equals(result.languageId())
                || (remoteStatus != CalibrationStatus.VALID && remoteStatus != CalibrationStatus.FAILED)) {
            throw invalidResponse();
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("calibrationId", result.id());
        detail.put("status", remoteStatus.name());
        detail.put("languageId", request.languageId());
        detail.put("testDataDigest", local.info().digest());
        if (result.benchmarkSummary() != null) {
            detail.put("sourceSha256", result.benchmarkSummary().sourceSha256());
            detail.put("verdict", result.benchmarkSummary().verdict());
        }
        transactions.executeWithoutResult(status -> audit(
                problemId,
                actorUserId,
                remoteStatus == CalibrationStatus.VALID ? "PROBLEM_CALIBRATED" : "PROBLEM_CALIBRATION_FAILED",
                detail));
        return calibration(result);
    }

    public PublishCheck publishCheck(String problemId, CallUser admin, String traceparent) {
        Snapshot local = transactions.execute(status -> snapshot(problemId, false));
        List<PublishCheckItem> checks = localChecks(local);
        if (local.info() == null) {
            checks.add(item(PublishCheckCode.CALIBRATION, false, "测试数据就绪后才能检查标定。"));
            return result(checks);
        }
        JudgingDtos.Readiness remote =
                judging.readiness(problemId, "cpp", local.info().digest(), admin, traceparent);
        mergeRemoteLanguage(checks, remote);
        // 在线节点的事实要保留到管理工作台：标定按钮据此提示并禁用。
        checks.add(
                remoteCheck(remote, "ONLINE_JUDGE_NODE", PublishCheckCode.ONLINE_JUDGE_NODE, "当前没有在线判题节点，请启动节点并等待注册。"));
        PublishCheckItem calibration =
                remoteCheck(remote, "CALIBRATION", PublishCheckCode.CALIBRATION, "该语言缺少与当前测试数据匹配的有效标定，请重新标定。");
        String hint = calibration.passed() ? recalibrationHint(local, remote.executionProfile()) : null;
        checks.add(hint == null ? calibration : item(PublishCheckCode.CALIBRATION, true, calibration.message() + hint));
        return result(checks);
    }

    /**
     * CORE 的参考解要配合驱动文件、并加上该语言的前缀才能跑；ACM 原样发送。合并规则见 contracts/judge-input.schema.json：
     * 用户文件 = 前缀 + 核心代码，前缀行数 = 前缀中换行符的个数。
     */
    private static JudgingDtos.CalibrationRequest calibrationRequest(
            String problemId, CalibrateProblemRequest request, Snapshot local) {
        String location = local.problem().testDataLocation();
        String digest = local.info().digest();
        if (local.problem().codeMode() != AdminProblemDtos.CodeMode.CORE) {
            return new JudgingDtos.CalibrationRequest(
                    problemId,
                    request.languageId(),
                    location,
                    digest,
                    request.cpuNs(),
                    request.memoryBytes(),
                    request.clockNs(),
                    request.referenceSource(),
                    null,
                    null);
        }
        String prefix = local.prefix().solutionPrefix();
        return new JudgingDtos.CalibrationRequest(
                problemId,
                request.languageId(),
                location,
                digest,
                request.cpuNs(),
                request.memoryBytes(),
                request.clockNs(),
                local.languages().getFirst().judgeTemplate(),
                prefix + request.referenceSource(),
                lineCount(prefix));
    }

    private static int lineCount(String prefix) {
        return (int) prefix.chars().filter(character -> character == '\n').count();
    }

    /**
     * 修改驱动代码或前缀不会让标定自动失效（2026-10-17 决定先只做提示）：两者晚于这条标定修改时，在 CALIBRATION 项后附一句提示。
     * 只比较时间，不比较内容；judging-service 没给出标定时间时不提示。
     */
    private static String recalibrationHint(Snapshot local, JudgingDtos.ExecutionProfile profile) {
        if (local.problem().codeMode() != AdminProblemDtos.CodeMode.CORE
                || profile == null
                || profile.calibratedAt() == null) {
            return null;
        }
        LocalDateTime calibratedAt = profile.calibratedAt();
        LocalDateTime templateAt = local.languages().getFirst().judgeTemplateUpdatedAt();
        if (templateAt != null && templateAt.isAfter(calibratedAt)) {
            return "驱动代码在最近一次标定后修改过，建议重新标定。";
        }
        if (local.prefix() != null && local.prefix().updatedAt().isAfter(calibratedAt)) {
            return "该语言的前缀代码在最近一次标定后修改过，建议重新标定。";
        }
        return null;
    }

    public Problem publish(String problemId, long rowVersion, CallUser admin, String traceparent, String actorUserId) {
        Snapshot before = transactions.execute(status -> snapshot(problemId, false));
        requireActive(before.problem());
        if (before.problem().visibility() == Visibility.PUBLIC) {
            return adminProblems.getProblem(problemId);
        }
        if (before.problem().rowVersion() != rowVersion) throw conflict();
        if (!publishCheck(problemId, admin, traceparent).ready()) {
            throw state("公开检查未通过，请先处理所有缺项。");
        }

        transactions.executeWithoutResult(status -> {
            Snapshot locked = snapshot(problemId, true);
            requireActive(locked.problem());
            if (locked.problem().rowVersion() != rowVersion) throw conflict();
            if (localChecks(locked).stream().anyMatch(value -> !value.passed())) {
                throw state("本地公开条件已改变，请重新检查。");
            }
            // 远程检查之后数据又被替换：检查的是旧数据，不能据此公开。
            if (!locked.info().digest().equals(before.info().digest())) {
                throw state("测试数据已改变，请重新检查。");
            }
            if (problems.publishProblem(problemId, now(), rowVersion) != 1) throw conflict();
            audit(
                    problemId,
                    actorUserId,
                    "PROBLEM_PUBLISHED",
                    Map.of("testDataDigest", locked.info().digest()));
        });
        return adminProblems.getProblem(problemId);
    }

    public Problem unpublish(String problemId, long rowVersion, String actorUserId) {
        transactions.executeWithoutResult(status -> {
            Snapshot locked = snapshot(problemId, true);
            requireActive(locked.problem());
            if (locked.problem().visibility() == Visibility.PRIVATE) return;
            if (locked.problem().rowVersion() != rowVersion) throw conflict();
            if (problems.unpublishProblem(problemId, now(), rowVersion) != 1) throw conflict();
            audit(problemId, actorUserId, "PROBLEM_UNPUBLISHED", Map.of());
        });
        return adminProblems.getProblem(problemId);
    }

    private Snapshot snapshot(String problemId, boolean lock) {
        ProblemRow problem = lock ? problems.findProblemForUpdate(problemId) : problems.findProblem(problemId);
        if (problem == null) throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "题目不存在。");
        TestDataStore.Info info = null;
        String dataProblem = "还没有上传测试数据。";
        if (problem.testDataLocation() != null) {
            try {
                info = testData.describe(problem.testDataLocation());
            } catch (TestDataStore.AssetException error) {
                dataProblem = "测试数据读取不到或已损坏，请重新上传。";
            }
        }
        List<LanguageRow> languages = problems.findLanguages(problemId);
        LanguagePrefixRow prefix = languages.isEmpty()
                ? null
                : problems.findLanguagePrefix(languages.getFirst().languageId());
        return new Snapshot(problem, problems.findSamples(problemId), languages, prefix, info, dataProblem);
    }

    private static void requireActive(ProblemRow problem) {
        if (problem.status() != AdminProblemDtos.ProblemStatus.ACTIVE) throw state("归档题目不能标定或公开。");
    }

    private List<PublishCheckItem> localChecks(Snapshot local) {
        ProblemRow problem = local.problem();
        boolean content = nonBlank(problem.title())
                && nonBlank(problem.statementMarkdown())
                && nonBlank(problem.inputDescriptionMarkdown())
                && nonBlank(problem.outputDescriptionMarkdown());
        boolean samples = !local.samples().isEmpty() && continuous(local.samples());
        String languageProblem = languageProblem(local);
        boolean language = languageProblem == null;
        boolean data = local.info() != null;
        ArrayList<PublishCheckItem> checks = new ArrayList<>(6);
        checks.add(item(PublishCheckCode.CONTENT, content, content ? "题面必填章节完整。" : "标题、题面、输入或输出说明不完整。"));
        checks.add(item(PublishCheckCode.SAMPLES, samples, samples ? "已有样例。" : "至少需要一个样例。"));
        checks.add(item(
                PublishCheckCode.LANGUAGE,
                language,
                language
                        ? (problem.codeMode() == AdminProblemDtos.CodeMode.CORE ? "C++ 核心代码配置有效。" : "C++ ACM 语言配置有效。")
                        : languageProblem));
        checks.add(item(
                PublishCheckCode.TEST_DATA,
                data,
                data ? "测试数据就绪：" + local.info().testcaseCount() + " 个测试点。" : local.dataProblem()));
        return checks;
    }

    /** 语言配置不能公开的原因；可以公开时返回 null。 */
    private static String languageProblem(Snapshot local) {
        if (local.languages().size() != 1
                || !"cpp".equals(local.languages().getFirst().languageId())) {
            return "目前只支持单一 C++ 语言配置。";
        }
        if (local.problem().codeMode() != AdminProblemDtos.CodeMode.CORE) {
            return null;
        }
        if (!nonBlank(local.languages().getFirst().judgeTemplate())) {
            return "核心代码题需要填写驱动代码。";
        }
        if (local.prefix() == null) {
            return "缺少 C++ 的前缀代码配置。";
        }
        return null;
    }

    private static boolean continuous(List<SampleRow> samples) {
        for (int index = 0; index < samples.size(); index++) {
            if (samples.get(index).ordinal() != index + 1) return false;
        }
        return true;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static PublishCheckItem remoteCheck(
            JudgingDtos.Readiness readiness, String remoteCode, PublishCheckCode publicCode, String fallback) {
        if (readiness.checks() != null) {
            for (JudgingDtos.ReadinessCheck check : readiness.checks()) {
                if (remoteCode.equals(check.code()))
                    return item(publicCode, check.passed(), safeMessage(check.message(), fallback));
            }
        }
        return item(publicCode, false, fallback);
    }

    private static void mergeRemoteLanguage(List<PublishCheckItem> checks, JudgingDtos.Readiness readiness) {
        PublishCheckItem local = checks.get(2);
        if (!local.passed()) return;
        PublishCheckItem language = remoteCheck(readiness, "LANGUAGE", PublishCheckCode.LANGUAGE, "没有在线节点支持 C++。");
        if (!language.passed()) checks.set(2, language);
    }

    private static String safeMessage(String message, String fallback) {
        if (message == null || message.isBlank()) return fallback;
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }

    private static PublishCheckItem item(PublishCheckCode code, boolean passed, String message) {
        return new PublishCheckItem(code, passed, message);
    }

    private static PublishCheck result(List<PublishCheckItem> checks) {
        var codes = checks.stream().map(PublishCheckItem::code).collect(java.util.stream.Collectors.toSet());
        var required = java.util.EnumSet.allOf(PublishCheckCode.class);
        required.remove(PublishCheckCode.ONLINE_JUDGE_NODE);
        if (codes.size() != checks.size() || !codes.containsAll(required))
            throw new IllegalStateException("Publish check must contain each base check exactly once");
        return new PublishCheck(checks.stream().allMatch(PublishCheckItem::passed), List.copyOf(checks));
    }

    private static AdminProblemDtos.LanguageCalibration calibration(JudgingDtos.Calibration value) {
        try {
            AdminProblemDtos.BenchmarkSummary summary = value.benchmarkSummary() == null
                    ? null
                    : new AdminProblemDtos.BenchmarkSummary(
                            value.benchmarkSummary().sourceSha256(),
                            JudgeVerdict.valueOf(value.benchmarkSummary().verdict()),
                            value.benchmarkSummary().maxCpuNs(),
                            value.benchmarkSummary().maxMemoryBytes(),
                            value.benchmarkSummary().maxClockNs());
            return new AdminProblemDtos.LanguageCalibration(
                    value.id(),
                    value.problemId(),
                    value.languageId(),
                    calibrationStatus(value.status()),
                    value.cpuNs(),
                    value.memoryBytes(),
                    value.clockNs(),
                    value.testDataDigest(),
                    summary,
                    value.errorMessage(),
                    value.createdAt(),
                    value.updatedAt(),
                    value.rowVersion());
        } catch (ProblemApiException error) {
            throw error;
        } catch (RuntimeException error) {
            throw invalidResponse();
        }
    }

    private static CalibrationStatus calibrationStatus(String value) {
        try {
            return CalibrationStatus.valueOf(value);
        } catch (RuntimeException error) {
            throw invalidResponse();
        }
    }

    private void audit(String problemId, String actorUserId, String action, Map<String, Object> detail) {
        problems.insertAudit(ids.next().toString(), problemId, actorUserId, action, null, writeJson(detail), now());
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Could not serialize publication audit", error);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static ProblemApiException invalidResponse() {
        return new ProblemApiException(HttpStatus.BAD_GATEWAY, "JUDGING_INVALID_RESPONSE", "判题服务响应格式无效。");
    }

    private static ProblemApiException state(String message) {
        return new ProblemApiException(HttpStatus.CONFLICT, "RESOURCE_STATE_CONFLICT", message);
    }

    private static ProblemApiException conflict() {
        return new ProblemApiException(HttpStatus.CONFLICT, "ROW_VERSION_CONFLICT", "资源已被其他窗口修改，请重新加载。");
    }

    /** info 为 null 表示测试数据缺失或读不到，原因在 dataProblem 里；prefix 是该语言的前缀，语言表里没有时为 null。 */
    private record Snapshot(
            ProblemRow problem,
            List<SampleRow> samples,
            List<LanguageRow> languages,
            LanguagePrefixRow prefix,
            TestDataStore.Info info,
            String dataProblem) {}
}
