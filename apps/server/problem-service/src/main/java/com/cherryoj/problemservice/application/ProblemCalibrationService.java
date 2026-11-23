package com.cherryoj.problemservice.application;

import static com.cherryoj.problemservice.application.ProblemReadinessChecks.*;

import com.cherryoj.identity.token.CallUser;
import com.cherryoj.problemservice.api.AdminProblemDtos;
import com.cherryoj.problemservice.api.AdminProblemDtos.CalibrateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.CalibrationStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.JudgeVerdict;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.application.ProblemReadinessChecks.PublicationSnapshot;
import com.cherryoj.problemservice.integration.judging.JudgingClient;
import com.cherryoj.problemservice.integration.judging.JudgingDtos;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguageRow;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 校验参考程序所需的题目状态，调用判题服务并保存标定审计。 */
@Service
public class ProblemCalibrationService {
    private final ProblemReadinessChecks readiness;
    private final JudgingClient judging;
    private final ProblemAuditWriter audit;
    private final TransactionTemplate transactions;

    ProblemCalibrationService(
            ProblemReadinessChecks readiness,
            JudgingClient judging,
            ProblemAuditWriter audit,
            PlatformTransactionManager transactionManager) {
        this.readiness = readiness;
        this.judging = judging;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public AdminProblemDtos.LanguageCalibration calibrate(
            String problemId, CalibrateProblemRequest request, CallUser admin, String traceparent, String actorUserId) {
        PublicationSnapshot local = transactions.execute(status -> readiness.load(problemId, false));
        requireActive(local.problem());
        if (localChecks(local).stream().anyMatch(value -> !value.passed())) {
            throw state("题面、样例、语言或测试数据尚未就绪。");
        }
        if (local.languages().stream()
                .noneMatch(language -> request.languageId().equals(language.languageId()))) {
            throw new ProblemApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_LANGUAGE", "当前题目不支持这门语言。");
        }
        LanguageRow selected = selected(local, request.languageId());
        if (languageProblem(local, selected) != null) throw state(languageProblem(local, selected));
        JudgingDtos.Calibration result =
                judging.calibrate(calibrationRequest(problemId, request, local), admin, traceparent);
        CalibrationStatus remoteStatus = calibrationStatus(result.status());
        if (!problemId.equals(result.problemId())
                || !request.languageId().equals(result.languageId())
                || !request.kind().equals(result.kind())
                || (remoteStatus != CalibrationStatus.VALID && remoteStatus != CalibrationStatus.FAILED)) {
            throw invalidResponse();
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("calibrationId", result.id());
        detail.put("status", remoteStatus.name());
        detail.put("languageId", request.languageId());
        detail.put("kind", request.kind());
        detail.put("testDataDigest", local.info().digest());
        if (result.benchmarkSummary() != null) {
            detail.put("sourceSha256", result.benchmarkSummary().sourceSha256());
            detail.put("verdict", result.benchmarkSummary().verdict());
        }
        transactions.executeWithoutResult(status -> audit.append(
                problemId,
                actorUserId,
                remoteStatus == CalibrationStatus.VALID ? "PROBLEM_CALIBRATED" : "PROBLEM_CALIBRATION_FAILED",
                detail));
        return calibration(result);
    }

    public JudgingDtos.LanguageLimitsList limits(String problemId, CallUser admin, String traceparent) {
        PublicationSnapshot local = transactions.execute(status -> readiness.load(problemId, false));
        if (local.info() == null)
            throw new ProblemApiException(HttpStatus.NOT_FOUND, "TEST_DATA_NOT_FOUND", local.dataProblem());
        var ids = local.languages().stream().map(LanguageRow::languageId).toList();
        var response = judging.limits(
                problemId, ids, local.info().digest(), local.info().maxTestcaseIoBytes(), admin, traceparent);
        if (response == null
                || response.languages() == null
                || !response.languages().stream()
                        .map(JudgingDtos.LanguageLimits::languageId)
                        .toList()
                        .equals(ids)) throw invalidResponse();
        return response;
    }

    /**
     * CORE 的参考解要配合驱动文件、并加上该语言的前缀才能跑；ACM 原样发送。合并规则见 contracts/judge-input.schema.json：
     * 用户文件 = 前缀 + 核心代码，前缀行数 = 前缀中换行符的个数。
     */
    private static JudgingDtos.CalibrationRequest calibrationRequest(
            String problemId, CalibrateProblemRequest request, PublicationSnapshot local) {
        String location = local.problem().testDataLocation();
        String digest = local.info().digest();
        if (local.problem().codeMode() != AdminProblemDtos.CodeMode.CORE) {
            return new JudgingDtos.CalibrationRequest(
                    problemId,
                    request.languageId(),
                    request.kind(),
                    location,
                    digest,
                    request.cpuNs(),
                    request.memoryBytes(),
                    request.clockNs(),
                    request.referenceSource(),
                    null,
                    null);
        }
        String prefix = local.prefixes().get(request.languageId()).solutionPrefix();
        return new JudgingDtos.CalibrationRequest(
                problemId,
                request.languageId(),
                request.kind(),
                location,
                digest,
                request.cpuNs(),
                request.memoryBytes(),
                request.clockNs(),
                selected(local, request.languageId()).judgeTemplate(),
                prefix + request.referenceSource(),
                lineCount(prefix));
    }

    private static int lineCount(String prefix) {
        return (int) prefix.chars().filter(character -> character == '\n').count();
    }

    private static LanguageRow selected(PublicationSnapshot local, String languageId) {
        return local.languages().stream()
                .filter(language -> language.languageId().equals(languageId))
                .findFirst()
                .orElseThrow();
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
                    value.kind(),
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

    private static ProblemApiException invalidResponse() {
        return new ProblemApiException(HttpStatus.BAD_GATEWAY, "JUDGING_INVALID_RESPONSE", "判题服务响应格式无效。");
    }
}
