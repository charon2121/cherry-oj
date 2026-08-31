package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.AdminProblemDtos;
import com.cherryoj.problemservice.api.AdminProblemDtos.CalibrateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.CalibrationStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.DeployTestDataRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.DeploymentStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.JudgeVerdict;
import com.cherryoj.problemservice.api.AdminProblemDtos.PublishCheck;
import com.cherryoj.problemservice.api.AdminProblemDtos.PublishCheckCode;
import com.cherryoj.problemservice.api.AdminProblemDtos.PublishCheckItem;
import com.cherryoj.problemservice.api.AdminProblemDtos.Version;
import com.cherryoj.problemservice.api.AdminProblemDtos.VersionStatus;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.api.TestDataDtos;
import com.cherryoj.problemservice.domain.UuidV7;
import com.cherryoj.problemservice.integration.judging.JudgingClient;
import com.cherryoj.problemservice.integration.judging.JudgingDtos;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguageRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.SampleRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.VersionRow;
import com.cherryoj.problemservice.persistence.TestDataMapper;
import com.cherryoj.problemservice.persistence.TestDataRows.TestDataRow;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProblemPublicationService {
    private final AdminProblemMapper problems;
    private final TestDataMapper testData;
    private final TestDataService testDataService;
    private final AdminProblemService adminProblems;
    private final JudgingClient judging;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    public ProblemPublicationService(
            AdminProblemMapper problems,
            TestDataMapper testData,
            TestDataService testDataService,
            AdminProblemService adminProblems,
            JudgingClient judging,
            UuidV7 ids,
            Clock clock,
            ObjectMapper json,
            PlatformTransactionManager transactionManager) {
        this.problems = problems;
        this.testData = testData;
        this.testDataService = testDataService;
        this.adminProblems = adminProblems;
        this.judging = judging;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public AdminProblemDtos.TestDataDeployment deploy(
            String problemId,
            String versionId,
            DeployTestDataRequest request,
            String delegatedJwt,
            String traceparent,
            String actorUserId) {
        DeploymentSnapshot snapshot = transactions.execute(status -> {
            Snapshot local = snapshot(problemId, versionId, true);
            requireActive(local.problem());
            requireRowVersion(local.version(), request.rowVersion());
            TestDataRow data = requireBoundReadyData(local);
            if (!data.id().equals(request.testDataVersionId())
                    || !data.contentSha256().equals(request.expectedSha256())) {
                throw state("部署请求与版本当前绑定的 READY 测试数据不一致。");
            }
            return new DeploymentSnapshot(data.id(), data.contentSha256());
        });

        JudgingDtos.Deployment deployed;
        try (TestDataService.ReadyAsset asset = testDataService.openReady(problemId, snapshot.testDataVersionId())) {
            if (!asset.contentSha256().equals(snapshot.sha256())) throw state("测试数据摘要已改变。");
            deployed = judging.deploy(new JudgingDtos.DeploymentMetadata(
                            asset.id(), asset.contentSha256(), manifest(asset.manifest())),
                    asset.stream(), delegatedJwt, traceparent);
        }
        catch (ProblemApiException error) {
            throw error;
        }
        catch (java.io.IOException error) {
            throw new ProblemApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "TEST_DATA_STORAGE_UNAVAILABLE", "测试数据资产暂时不可用。");
        }
        AdminProblemDtos.TestDataDeployment response = deployment(deployed);
        if (!response.testDataVersionId().equals(snapshot.testDataVersionId())
                || !response.expectedSha256().equals(snapshot.sha256())
                || response.status() != DeploymentStatus.READY
                || !snapshot.sha256().equals(response.deployedSha256())) {
            throw invalidResponse();
        }
        audit(problemId, versionId, actorUserId, "TEST_DATA_DEPLOYED", Map.of(
                "testDataVersionId", snapshot.testDataVersionId(),
                "expectedSha256", snapshot.sha256(),
                "environmentId", response.environmentId(),
                "status", response.status().name()));
        return response;
    }

    public AdminProblemDtos.LanguageCalibration calibrate(
            String problemId,
            String versionId,
            CalibrateProblemRequest request,
            String delegatedJwt,
            String traceparent,
            String actorUserId) {
        ValidationSnapshot start = transactions.execute(status -> beginValidation(problemId, versionId, request, actorUserId));
        JudgingDtos.Calibration result;
        try {
            result = judging.calibrate(new JudgingDtos.CalibrationRequest(
                            problemId, versionId, start.testDataVersionId(), start.sha256(), request.languageId(),
                            request.cpuNs(), request.memoryBytes(), request.clockNs(), request.referenceSource()),
                    delegatedJwt, traceparent);
            CalibrationStatus remoteStatus = calibrationStatus(result.status());
            if (!versionId.equals(result.problemVersionId()) || !request.languageId().equals(result.languageId())
                    || (remoteStatus != CalibrationStatus.VALID && remoteStatus != CalibrationStatus.FAILED)) {
                throw invalidResponse();
            }
            VersionStatus target = remoteStatus == CalibrationStatus.VALID
                    ? VersionStatus.READY_FOR_REVIEW : VersionStatus.DRAFT;
            finishValidation(problemId, versionId, start.validatingRowVersion(), target, actorUserId, result);
            return calibration(result);
        }
        catch (RuntimeException error) {
            restoreDraft(problemId, versionId, start.validatingRowVersion(), actorUserId, errorCode(error));
            throw error;
        }
    }

    public PublishCheck publishCheck(
            String problemId,
            String versionId,
            String delegatedJwt,
            String traceparent) {
        Snapshot local = transactions.execute(status -> snapshot(problemId, versionId, false));
        List<PublishCheckItem> checks = localChecks(local);
        TestDataRow data = readyData(local);
        if (data == null) {
            checks.add(item(PublishCheckCode.DEPLOYMENT, false, "绑定的测试数据尚未 READY。"));
            checks.add(item(PublishCheckCode.CALIBRATION, false, "测试数据就绪后才能检查校准。"));
            return result(null, checks);
        }
        JudgingDtos.Readiness remote = judging.readiness(
                versionId, data.id(), data.contentSha256(), "cpp", delegatedJwt, traceparent);
        mergeRemoteLanguage(checks, remote);
        checks.add(remoteCheck(remote, "DEPLOYMENT", PublishCheckCode.DEPLOYMENT, "当前环境缺少 READY 部署。"));
        checks.add(remoteCheck(remote, "CALIBRATION", PublishCheckCode.CALIBRATION, "当前环境缺少 VALID 校准。"));
        return result(remote.environmentId(), checks);
    }

    public Version publish(
            String problemId,
            String versionId,
            long rowVersion,
            String delegatedJwt,
            String traceparent,
            String actorUserId) {
        Snapshot before = transactions.execute(status -> snapshot(problemId, versionId, false));
        if (before.version().status() == VersionStatus.PUBLISHED
                && versionId.equals(before.problem().currentPublishedVersionId())) {
            return adminProblems.getVersion(problemId, versionId);
        }
        if (before.version().rowVersion() != rowVersion) throw conflict();
        if (before.version().status() != VersionStatus.READY_FOR_REVIEW) {
            throw state("只有 READY_FOR_REVIEW 版本可以发布。");
        }
        PublishCheck check = publishCheck(problemId, versionId, delegatedJwt, traceparent);
        if (!check.ready()) throw state("发布检查未通过，请先处理所有缺项。");

        transactions.executeWithoutResult(status -> {
            Snapshot locked = snapshot(problemId, versionId, true);
            requireActive(locked.problem());
            requireRowVersion(locked.version(), rowVersion);
            if (locked.version().status() != VersionStatus.READY_FOR_REVIEW) {
                throw state("题目版本状态已改变，请重新检查。");
            }
            List<PublishCheckItem> local = localChecks(locked);
            if (local.stream().anyMatch(value -> !value.passed())) {
                throw state("本地发布条件已改变，请重新检查。");
            }
            TestDataRow lockedData = requireBoundReadyData(locked);
            if (!lockedData.id().equals(before.version().testDataVersionId())
                    || !lockedData.contentSha256().equals(requireBoundReadyData(before).contentSha256())) {
                throw state("测试数据绑定已改变，请重新检查。");
            }
            LocalDateTime now = now();
            if (problems.publishVersion(problemId, versionId, actorUserId, now, rowVersion) != 1) throw conflict();
            if (problems.pointToPublishedVersion(problemId, versionId, now) != 1) {
                throw state("题目状态已改变，发布已回滚。");
            }
            auditInTransaction(problemId, versionId, actorUserId, "PROBLEM_VERSION_PUBLISHED", Map.of(
                    "versionNo", locked.version().versionNo(),
                    "testDataVersionId", lockedData.id(),
                    "contentSha256", lockedData.contentSha256(),
                    "environmentId", check.environmentId()));
        });
        return adminProblems.getVersion(problemId, versionId);
    }

    private ValidationSnapshot beginValidation(
            String problemId,
            String versionId,
            CalibrateProblemRequest request,
            String actorUserId) {
        Snapshot local = snapshot(problemId, versionId, true);
        requireActive(local.problem());
        requireRowVersion(local.version(), request.rowVersion());
        if (local.version().status() != VersionStatus.DRAFT) throw state("只有 DRAFT 版本可以开始验证。");
        List<PublishCheckItem> checks = localChecks(local);
        if (checks.stream().limit(4).anyMatch(value -> !value.passed())) {
            throw state("题面、样例、语言或测试数据尚未就绪。");
        }
        TestDataRow data = requireBoundReadyData(local);
        LocalDateTime now = now();
        if (problems.beginValidation(problemId, versionId, now, request.rowVersion()) != 1) throw conflict();
        auditInTransaction(problemId, versionId, actorUserId, "PROBLEM_VALIDATION_STARTED", Map.of(
                "testDataVersionId", data.id(), "contentSha256", data.contentSha256(),
                "languageId", request.languageId(), "cpuNs", request.cpuNs(),
                "memoryBytes", request.memoryBytes()));
        return new ValidationSnapshot(data.id(), data.contentSha256(), request.rowVersion() + 1);
    }

    private void finishValidation(
            String problemId,
            String versionId,
            long expectedRowVersion,
            VersionStatus target,
            String actorUserId,
            JudgingDtos.Calibration calibration) {
        transactions.executeWithoutResult(status -> {
            requireActive(requireProblem(problemId, true));
            if (problems.finishValidation(problemId, versionId, target, now(), expectedRowVersion) != 1) {
                throw conflict();
            }
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("calibrationId", calibration.id());
            detail.put("environmentId", calibration.environmentId());
            detail.put("status", calibration.status());
            if (calibration.benchmarkSummary() != null) {
                detail.put("sourceSha256", calibration.benchmarkSummary().sourceSha256());
                detail.put("verdict", calibration.benchmarkSummary().verdict());
            }
            auditInTransaction(problemId, versionId, actorUserId,
                    target == VersionStatus.READY_FOR_REVIEW
                            ? "PROBLEM_VALIDATION_SUCCEEDED" : "PROBLEM_VALIDATION_FAILED",
                    detail);
        });
    }

    private void restoreDraft(
            String problemId,
            String versionId,
            long expectedRowVersion,
            String actorUserId,
            String failureCode) {
        try {
            transactions.executeWithoutResult(status -> {
                if (problems.finishValidation(problemId, versionId, VersionStatus.DRAFT, now(), expectedRowVersion) == 1) {
                    auditInTransaction(problemId, versionId, actorUserId, "PROBLEM_VALIDATION_FAILED",
                            Map.of("failureCode", failureCode));
                }
            });
        }
        catch (RuntimeException ignored) {
            // A concurrent recovery or state transition owns the row now; never mask the original remote failure.
        }
    }

    private Snapshot snapshot(String problemId, String versionId, boolean lock) {
        ProblemRow problem = requireProblem(problemId, lock);
        VersionRow version = lock
                ? problems.findVersionForUpdate(problemId, versionId)
                : problems.findVersion(problemId, versionId);
        if (version == null) throw new ProblemApiException(
                HttpStatus.NOT_FOUND, "PROBLEM_VERSION_NOT_FOUND", "题目版本不存在。");
        return new Snapshot(problem, version, problems.findSamples(versionId), problems.findLanguages(versionId),
                version.testDataVersionId() == null ? null : testData.find(problemId, version.testDataVersionId()));
    }

    private ProblemRow requireProblem(String problemId, boolean lock) {
        ProblemRow problem = lock ? problems.findProblemForUpdate(problemId) : problems.findProblem(problemId);
        if (problem == null) throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "题目不存在。");
        return problem;
    }

    private static void requireActive(ProblemRow problem) {
        if (problem.status() != AdminProblemDtos.ProblemStatus.ACTIVE) throw state("归档题目不能部署、验证或发布。");
    }

    private List<PublishCheckItem> localChecks(Snapshot local) {
        boolean content = nonBlank(local.version().title())
                && nonBlank(local.version().statementMarkdown())
                && nonBlank(local.version().inputDescriptionMarkdown())
                && nonBlank(local.version().outputDescriptionMarkdown());
        boolean samples = !local.samples().isEmpty() && continuous(local.samples());
        boolean language = local.version().codeMode() == AdminProblemDtos.CodeMode.ACM
                && local.languages().size() == 1 && "cpp".equals(local.languages().getFirst().languageId());
        boolean data = readyData(local) != null;
        ArrayList<PublishCheckItem> checks = new ArrayList<>(6);
        checks.add(item(PublishCheckCode.CONTENT, content,
                content ? "题面必填章节完整。" : "标题、题面、输入或输出说明不完整。"));
        checks.add(item(PublishCheckCode.SAMPLES, samples,
                samples ? "样例顺序完整。" : "至少需要一个 ordinal 连续的样例。"));
        checks.add(item(PublishCheckCode.LANGUAGE, language,
                language ? "C++ ACM 语言配置有效。" : "首版只支持单一 C++ ACM 语言配置。"));
        checks.add(item(PublishCheckCode.TEST_DATA, data,
                data ? "已绑定本题 READY 测试数据。" : "未绑定本题 READY 测试数据。"));
        return checks;
    }

    private static TestDataRow readyData(Snapshot local) {
        TestDataRow data = local.data();
        return data != null
                && data.status() == TestDataDtos.Status.READY
                && data.problemId().equals(local.problem().id())
                && data.contentSha256() != null
                && data.manifestJson() != null
                ? data : null;
    }

    private static TestDataRow requireBoundReadyData(Snapshot local) {
        TestDataRow data = readyData(local);
        if (data == null) throw state("版本必须绑定本题 READY 测试数据。");
        return data;
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
            JudgingDtos.Readiness readiness,
            String remoteCode,
            PublishCheckCode publicCode,
            String fallback) {
        if (readiness.checks() != null) {
            for (JudgingDtos.ReadinessCheck check : readiness.checks()) {
                if (remoteCode.equals(check.code())) return item(publicCode, check.passed(), safeMessage(check.message(), fallback));
            }
        }
        return item(publicCode, false, fallback);
    }

    private static void mergeRemoteLanguage(List<PublishCheckItem> checks, JudgingDtos.Readiness readiness) {
        PublishCheckItem local = checks.get(2);
        if (!local.passed()) return;
        PublishCheckItem active = remoteCheck(
                readiness, "ACTIVE_ENVIRONMENT", PublishCheckCode.LANGUAGE, "没有 ACTIVE 判题环境。");
        PublishCheckItem language = remoteCheck(
                readiness, "LANGUAGE", PublishCheckCode.LANGUAGE, "C++ 未在当前环境启用。");
        if (!active.passed()) checks.set(2, active);
        else if (!language.passed()) checks.set(2, language);
    }

    private static String safeMessage(String message, String fallback) {
        if (message == null || message.isBlank()) return fallback;
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }

    private static PublishCheckItem item(PublishCheckCode code, boolean passed, String message) {
        return new PublishCheckItem(code, passed, message);
    }

    private static PublishCheck result(String environmentId, List<PublishCheckItem> checks) {
        if (checks.size() != 6) throw new IllegalStateException("Publish check must contain exactly six checks");
        return new PublishCheck(
                environmentId != null && checks.stream().allMatch(PublishCheckItem::passed),
                environmentId,
                List.copyOf(checks));
    }

    private static JudgingDtos.Manifest manifest(TestDataDtos.Manifest manifest) {
        if (manifest == null) throw state("READY 测试数据缺少 manifest。");
        return new JudgingDtos.Manifest(manifest.caseCount(), manifest.totalBytes(), manifest.files().stream()
                .map(file -> new JudgingDtos.ManifestFile(file.name(), file.sizeBytes(), file.sha256())).toList());
    }

    private static AdminProblemDtos.TestDataDeployment deployment(JudgingDtos.Deployment value) {
        try {
            if (value.testDataVersionId() == null || value.environmentId() == null
                    || value.environmentName() == null || value.expectedSha256() == null
                    || value.status() == null || value.updatedAt() == null) throw invalidResponse();
            return new AdminProblemDtos.TestDataDeployment(
                    value.testDataVersionId(), value.environmentId(), value.environmentName(), value.expectedSha256(),
                    DeploymentStatus.valueOf(value.status()), value.deployedSha256(), value.deployedAt(),
                    value.errorMessage(), value.updatedAt(), value.rowVersion());
        }
        catch (RuntimeException error) {
            throw invalidResponse();
        }
    }

    private static AdminProblemDtos.LanguageCalibration calibration(JudgingDtos.Calibration value) {
        try {
            AdminProblemDtos.BenchmarkSummary summary = value.benchmarkSummary() == null ? null
                    : new AdminProblemDtos.BenchmarkSummary(
                            value.benchmarkSummary().sourceSha256(),
                            JudgeVerdict.valueOf(value.benchmarkSummary().verdict()),
                            value.benchmarkSummary().maxCpuNs(), value.benchmarkSummary().maxMemoryBytes(),
                            value.benchmarkSummary().maxClockNs());
            return new AdminProblemDtos.LanguageCalibration(
                    value.id(), value.problemVersionId(), value.languageId(), value.environmentId(),
                    calibrationStatus(value.status()), value.cpuNs(), value.memoryBytes(), value.clockNs(), summary,
                    value.errorMessage(), value.createdAt(), value.updatedAt(), value.rowVersion());
        }
        catch (ProblemApiException error) {
            throw error;
        }
        catch (RuntimeException error) {
            throw invalidResponse();
        }
    }

    private static CalibrationStatus calibrationStatus(String value) {
        try {
            return CalibrationStatus.valueOf(value);
        }
        catch (RuntimeException error) {
            throw invalidResponse();
        }
    }

    private void audit(
            String problemId,
            String versionId,
            String actorUserId,
            String action,
            Map<String, Object> detail) {
        transactions.executeWithoutResult(status -> auditInTransaction(
                problemId, versionId, actorUserId, action, detail));
    }

    private void auditInTransaction(
            String problemId,
            String versionId,
            String actorUserId,
            String action,
            Map<String, Object> detail) {
        problems.insertAudit(ids.next().toString(), problemId, versionId, actorUserId, action, null,
                writeJson(detail), now());
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        }
        catch (Exception error) {
            throw new IllegalStateException("Could not serialize publication audit", error);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static void requireRowVersion(VersionRow version, long expected) {
        if (version.rowVersion() != expected) throw conflict();
    }

    private static String errorCode(RuntimeException error) {
        return error instanceof ProblemApiException problem ? problem.code() : "JUDGING_INVALID_RESPONSE";
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

    private record Snapshot(
            ProblemRow problem,
            VersionRow version,
            List<SampleRow> samples,
            List<LanguageRow> languages,
            TestDataRow data) {}

    private record DeploymentSnapshot(String testDataVersionId, String sha256) {}
    private record ValidationSnapshot(String testDataVersionId, String sha256, long validatingRowVersion) {}
}
