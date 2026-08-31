package com.cherryoj.judgingservice.application;

import com.cherryoj.judgingservice.api.JudgingApiException;
import com.cherryoj.judgingservice.api.JudgingDtos;
import com.cherryoj.judgingservice.api.JudgingDtos.BenchmarkSummary;
import com.cherryoj.judgingservice.api.JudgingDtos.Calibration;
import com.cherryoj.judgingservice.api.JudgingDtos.CalibrationRequest;
import com.cherryoj.judgingservice.api.JudgingDtos.Deployment;
import com.cherryoj.judgingservice.api.JudgingDtos.DeploymentMetadata;
import com.cherryoj.judgingservice.api.JudgingDtos.ExecutionProfile;
import com.cherryoj.judgingservice.api.JudgingDtos.Readiness;
import com.cherryoj.judgingservice.api.JudgingDtos.ReadinessCheck;
import com.cherryoj.judgingservice.domain.UuidV7;
import com.cherryoj.judgingservice.judge.JudgeGateway;
import com.cherryoj.judgingservice.judge.JudgeGateway.JudgeCallException;
import com.cherryoj.judgingservice.persistence.JudgingRepository;
import com.cherryoj.judgingservice.persistence.JudgingRepository.CalibrationRow;
import com.cherryoj.judgingservice.persistence.JudgingRepository.DeploymentRow;
import com.cherryoj.judgingservice.persistence.JudgingRepository.EnvironmentRow;
import com.cherryoj.judgingservice.storage.TestDataDeploymentStore;
import com.cherryoj.judgingservice.storage.TestDataDeploymentStore.DeploymentException;
import com.cherryoj.judgingservice.storage.TestDataDeploymentStore.Installed;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class JudgingReadinessService {
    private static final int MAX_SAFE_ERROR = 128;
    private final JudgingRepository repository;
    private final TestDataDeploymentStore store;
    private final JudgeGateway judge;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final ReentrantLock[] deploymentLocks = java.util.stream.IntStream.range(0, 64)
            .mapToObj(ignored -> new ReentrantLock()).toArray(ReentrantLock[]::new);

    public JudgingReadinessService(JudgingRepository repository, TestDataDeploymentStore store,
                                   JudgeGateway judge, UuidV7 ids, Clock clock, ObjectMapper json,
                                   PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.store = store;
        this.judge = judge;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Deployment deploy(DeploymentMetadata metadata, InputStream archive,
                             String actorId, String traceId) {
        ReentrantLock lock = deploymentLocks[(metadata.testDataVersionId().hashCode() & Integer.MAX_VALUE)
                % deploymentLocks.length];
        lock.lock();
        try {
            BeginDeployment begin = transactions.execute(status -> beginDeployment(metadata, actorId, traceId));
            if (begin.ready() != null) return begin.ready();

            Installed installed;
            try {
                installed = store.deploy(metadata.testDataVersionId(), metadata.expectedSha256(),
                        metadata.manifest(), archive);
            }
            catch (DeploymentException error) {
                failDeployment(metadata.testDataVersionId(), begin.environment().id(), error.getMessage(), actorId, traceId);
                throw deploymentProblem(error);
            }

            try {
                return transactions.execute(status -> finishDeployment(
                        metadata, begin.environment(), installed, actorId, traceId));
            }
            catch (RuntimeException error) {
                store.rollback(installed);
                failDeployment(metadata.testDataVersionId(), begin.environment().id(),
                        "DEPLOYMENT_FINALIZE_FAILED", actorId, traceId);
                if (error instanceof JudgingApiException api) throw api;
                throw unavailable("DEPLOYMENT_FINALIZE_FAILED", "测试数据部署暂时无法完成。");
            }
        }
        finally {
            lock.unlock();
        }
    }

    public Calibration calibrate(CalibrationRequest request, String actorId, String traceId) {
        if (request.referenceSource().getBytes(StandardCharsets.UTF_8).length > 1_048_576) {
            throw new JudgingApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "REFERENCE_SOURCE_TOO_LARGE", "参考源码超过安全限额。");
        }
        CalibrationStart start = transactions.execute(status -> startCalibration(request, actorId, traceId));
        String sourceSha = sha256(request.referenceSource().getBytes(StandardCharsets.UTF_8));
        JudgeGateway.JudgeResult result;
        try {
            result = judge.judge(start.environment().endpointRef(), new JudgeGateway.JudgeRequest(
                    start.calibrationId(), request.problemId(), request.problemVersionId(),
                    request.testDataVersionId(), request.languageId(), request.referenceSource(),
                    new JudgeGateway.Limits(request.cpuNs(), request.memoryBytes(), request.clockNs()), "submit"), traceId);
        }
        catch (JudgeCallException error) {
            BenchmarkSummary summary = new BenchmarkSummary(sourceSha, "SE", null, null, null);
            finishFailedCalibration(start.calibrationId(), summary, safe(error.getMessage()), actorId, traceId);
            return mapCalibration(repository.findCalibration(start.calibrationId()));
        }

        String verdict = safeVerdict(result.verdict());
        BenchmarkSummary summary = new BenchmarkSummary(sourceSha, verdict,
                nonNegative(result.cpuNs()), nonNegative(result.memoryBytes()), null);
        if (!"AC".equals(verdict) || !start.environment().fingerprint().equals(result.environmentFingerprint())) {
            String error = !start.environment().fingerprint().equals(result.environmentFingerprint())
                    ? "JUDGE_ENVIRONMENT_FINGERPRINT_MISMATCH" : "REFERENCE_" + verdict;
            finishFailedCalibration(start.calibrationId(), summary, error, actorId, traceId);
            return mapCalibration(repository.findCalibration(start.calibrationId()));
        }

        return transactions.execute(status -> finishValidCalibration(
                start, request, summary, actorId, traceId));
    }

    public Readiness readiness(String problemVersionId, String testDataVersionId,
                               String expectedSha256, String languageId) {
        TransactionTemplate read = new TransactionTemplate(transactions.getTransactionManager());
        read.setReadOnly(true);
        return read.execute(status -> resolveReadiness(problemVersionId, testDataVersionId,
                expectedSha256, languageId));
    }

    private BeginDeployment beginDeployment(DeploymentMetadata metadata, String actorId, String traceId) {
        EnvironmentRow environment = requireActive(true);
        DeploymentRow row = repository.findDeployment(metadata.testDataVersionId(), environment.id(), true);
        if (row != null) {
            if (!row.expectedSha256().equals(metadata.expectedSha256())) {
                throw conflict("DEPLOYMENT_HASH_CONFLICT", "相同测试数据版本不能部署不同摘要。");
            }
            if ("READY".equals(row.status()) && row.expectedSha256().equals(row.deployedSha256())) {
                return new BeginDeployment(environment, mapDeployment(row, environment), row.rowVersion());
            }
            if ("FAILED".equals(row.status())) {
                if (repository.retryFailedDeployment(row.testDataVersionId(), row.environmentId(), now(), row.rowVersion()) != 1) {
                    throw conflict("DEPLOYMENT_STATE_CONFLICT", "部署状态已改变，请重试。");
                }
                row = repository.findDeployment(row.testDataVersionId(), row.environmentId(), false);
            }
            else {
                throw conflict("DEPLOYMENT_IN_PROGRESS", "该测试数据版本正在部署。");
            }
        }
        else {
            repository.insertDeploying(metadata.testDataVersionId(), environment.id(), metadata.expectedSha256(), now());
            row = repository.findDeployment(metadata.testDataVersionId(), environment.id(), false);
        }
        audit("DEPLOYMENT", metadata.testDataVersionId(), actorId, "TEST_DATA_DEPLOYMENT_STARTED", traceId,
                Map.of("testDataVersionId", metadata.testDataVersionId(), "environmentId", environment.id(),
                        "expectedSha256", metadata.expectedSha256()));
        return new BeginDeployment(environment, null, row.rowVersion());
    }

    private Deployment finishDeployment(DeploymentMetadata metadata, EnvironmentRow expectedEnvironment,
                                        Installed installed, String actorId, String traceId) {
        EnvironmentRow active = requireActive(true);
        if (!active.id().equals(expectedEnvironment.id())) {
            throw conflict("ACTIVE_ENVIRONMENT_CHANGED", "部署期间 ACTIVE 环境已经改变。");
        }
        DeploymentRow row = repository.findDeployment(metadata.testDataVersionId(), active.id(), true);
        if (row == null || !"DEPLOYING".equals(row.status())
                || !row.expectedSha256().equals(installed.sha256())) {
            throw conflict("DEPLOYMENT_STATE_CONFLICT", "部署状态已改变，请重试。");
        }
        if (repository.markDeploymentReady(row.testDataVersionId(), row.environmentId(), installed.sha256(),
                now(), row.rowVersion()) != 1) {
            throw conflict("DEPLOYMENT_STATE_CONFLICT", "部署状态已改变，请重试。");
        }
        audit("DEPLOYMENT", row.testDataVersionId(), actorId, "TEST_DATA_DEPLOYMENT_READY", traceId,
                Map.of("testDataVersionId", row.testDataVersionId(), "environmentId", active.id(),
                        "deployedSha256", installed.sha256(), "fileCount", metadata.manifest().files().size()));
        return mapDeployment(repository.findDeployment(row.testDataVersionId(), row.environmentId(), false), active);
    }

    private void failDeployment(String testDataId, String environmentId, String error,
                                String actorId, String traceId) {
        try {
            transactions.executeWithoutResult(status -> {
                String safeError = safe(error);
                if (repository.markDeploymentFailed(testDataId, environmentId, safeError, now()) == 1) {
                    audit("DEPLOYMENT", testDataId, actorId, "TEST_DATA_DEPLOYMENT_FAILED", traceId,
                            Map.of("testDataVersionId", testDataId, "environmentId", environmentId,
                                    "failureCode", safeError));
                }
            });
        }
        catch (RuntimeException ignored) { }
    }

    private CalibrationStart startCalibration(CalibrationRequest request, String actorId, String traceId) {
        EnvironmentRow environment = requireActive(true);
        requireLanguageAndDeployment(environment, request.languageId(), request.testDataVersionId(),
                request.expectedSha256());
        String id = ids.next().toString();
        repository.insertCalibration(id, request.problemVersionId(), request.languageId(), environment.id(),
                request.cpuNs(), request.memoryBytes(), request.clockNs(), now());
        audit("CALIBRATION", id, actorId, "CALIBRATION_STARTED", traceId,
                Map.of("calibrationId", id, "problemVersionId", request.problemVersionId(),
                        "testDataVersionId", request.testDataVersionId(), "environmentId", environment.id(),
                        "languageId", request.languageId(), "cpuNs", request.cpuNs(),
                        "memoryBytes", request.memoryBytes()));
        return new CalibrationStart(id, environment);
    }

    private Calibration finishValidCalibration(CalibrationStart start, CalibrationRequest request,
                                               BenchmarkSummary summary, String actorId, String traceId) {
        EnvironmentRow active = requireActive(true);
        if (!active.id().equals(start.environment().id())) {
            throw conflict("ACTIVE_ENVIRONMENT_CHANGED", "校准期间 ACTIVE 环境已经改变。");
        }
        requireLanguageAndDeployment(active, request.languageId(), request.testDataVersionId(), request.expectedSha256());
        CalibrationRow previous = repository.findValid(request.problemVersionId(), request.languageId(), active.id(), true);
        if (previous != null && repository.supersede(previous.id(), now(), previous.rowVersion()) != 1) {
            throw conflict("CALIBRATION_STATE_CONFLICT", "有效校准已经改变，请重试。");
        }
        String previousId = previous == null ? null : previous.id();
        if (repository.markCalibrationValid(start.calibrationId(), actorId, previousId, writeJson(summary), now()) != 1) {
            throw conflict("CALIBRATION_STATE_CONFLICT", "校准状态已经改变，请重试。");
        }
        audit("CALIBRATION", start.calibrationId(), actorId, "CALIBRATION_VALIDATED", traceId,
                Map.of("calibrationId", start.calibrationId(), "problemVersionId", request.problemVersionId(),
                        "environmentId", active.id(), "languageId", request.languageId(),
                        "sourceSha256", summary.sourceSha256(), "verdict", summary.verdict()));
        return mapCalibration(repository.findCalibration(start.calibrationId()));
    }

    private void finishFailedCalibration(String id, BenchmarkSummary summary, String error,
                                         String actorId, String traceId) {
        transactions.executeWithoutResult(status -> {
            if (repository.markCalibrationFailed(id, writeJson(summary), safe(error), now()) != 1) {
                throw conflict("CALIBRATION_STATE_CONFLICT", "校准状态已经改变，请重试。");
            }
            audit("CALIBRATION", id, actorId, "CALIBRATION_FAILED", traceId,
                    Map.of("calibrationId", id, "sourceSha256", summary.sourceSha256(),
                            "verdict", summary.verdict(), "failureCode", safe(error)));
        });
    }

    private Readiness resolveReadiness(String problemVersionId, String testDataVersionId,
                                       String expectedSha256, String languageId) {
        ArrayList<ReadinessCheck> checks = new ArrayList<>();
        EnvironmentRow environment = repository.findActive(false);
        checks.add(check("ACTIVE_ENVIRONMENT", environment != null,
                environment == null ? "没有 ACTIVE 判题环境。" : "ACTIVE 判题环境可用。"));
        boolean language = environment != null && repository.languageEnabled(environment.id(), languageId);
        checks.add(check("LANGUAGE", language,
                language ? "语言在当前环境已启用。" : "语言未在当前环境启用。"));
        DeploymentRow deployment = environment == null ? null
                : repository.findDeployment(testDataVersionId, environment.id(), false);
        boolean deployed = deployment != null && "READY".equals(deployment.status())
                && expectedSha256.equals(deployment.expectedSha256())
                && expectedSha256.equals(deployment.deployedSha256());
        checks.add(check("DEPLOYMENT", deployed,
                deployed ? "测试数据已按预期摘要部署。" : "测试数据尚未 READY 或摘要不匹配。"));
        CalibrationRow calibration = environment == null ? null
                : repository.findValid(problemVersionId, languageId, environment.id(), false);
        boolean calibrated = calibration != null;
        checks.add(check("CALIBRATION", calibrated,
                calibrated ? "当前环境存在 VALID 校准。" : "当前环境缺少 VALID 校准。"));
        boolean ready = checks.stream().allMatch(ReadinessCheck::passed);
        ExecutionProfile profile = ready ? new ExecutionProfile(environment.id(), environment.fingerprint(),
                environment.endpointRef(), calibration.id(), calibration.cpuNs(), calibration.memoryBytes(),
                calibration.clockNs()) : null;
        return new Readiness(ready, environment == null ? null : environment.id(), checks, profile);
    }

    private void requireLanguageAndDeployment(EnvironmentRow environment, String languageId,
                                              String testDataVersionId, String expectedSha) {
        if (!repository.languageEnabled(environment.id(), languageId)) {
            throw conflict("LANGUAGE_NOT_ENABLED", "语言未在当前 ACTIVE 环境启用。");
        }
        DeploymentRow deployment = repository.findDeployment(testDataVersionId, environment.id(), true);
        if (deployment == null || !"READY".equals(deployment.status())
                || !expectedSha.equals(deployment.expectedSha256())
                || !expectedSha.equals(deployment.deployedSha256())) {
            throw conflict("DEPLOYMENT_NOT_READY", "测试数据未在当前 ACTIVE 环境按预期摘要部署。");
        }
    }

    private EnvironmentRow requireActive(boolean lock) {
        EnvironmentRow environment = repository.findActive(lock);
        if (environment == null) throw unavailable("ACTIVE_ENVIRONMENT_MISSING", "没有 ACTIVE 判题环境。");
        return environment;
    }

    private void audit(String type, String aggregateId, String actorId, String action,
                       String traceId, Map<String, Object> detail) {
        repository.insertAudit(ids.next().toString(), type, aggregateId, actorId, action,
                safeTrace(traceId), writeJson(detail), now());
    }

    private Deployment mapDeployment(DeploymentRow row, EnvironmentRow environment) {
        return new Deployment(row.testDataVersionId(), row.environmentId(), environment.name(),
                row.expectedSha256(), row.status(), row.deployedSha256(), row.deployedAt(),
                row.errorMessage(), row.updatedAt(), row.rowVersion());
    }

    private Calibration mapCalibration(CalibrationRow row) {
        BenchmarkSummary summary = row.benchmarkSummaryJson() == null ? null
                : json.readValue(row.benchmarkSummaryJson(), BenchmarkSummary.class);
        return new Calibration(row.id(), row.problemVersionId(), row.languageId(), row.environmentId(),
                row.status(), row.cpuNs(), row.memoryBytes(), row.clockNs(), summary, row.errorMessage(),
                row.createdAt(), row.updatedAt(), row.rowVersion());
    }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (RuntimeException error) { throw new IllegalStateException("could not serialize safe metadata", error); }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static Long nonNegative(Long value) { return value == null || value < 0 ? null : value; }
    private static String safeVerdict(String verdict) {
        return verdict != null && java.util.Set.of("AC", "WA", "TLE", "MLE", "RE", "CE", "SE").contains(verdict)
                ? verdict : "SE";
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
    private static JudgingApiException deploymentProblem(DeploymentException error) {
        return switch (error.kind()) {
            case INVALID -> new JudgingApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_TEST_DATA_DEPLOYMENT", "测试数据 ZIP 与 manifest 不匹配。");
            case TOO_LARGE -> new JudgingApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE", "测试数据 ZIP 超过安全限额。");
            case CONFLICT -> conflict("DEPLOYMENT_DIRECTORY_CONFLICT", "判题目录已存在，拒绝覆盖。");
            case STORAGE -> unavailable("DEPLOYMENT_STORAGE_UNAVAILABLE", "判题数据目录暂时不可用。");
        };
    }
    private static JudgingApiException conflict(String code, String message) {
        return new JudgingApiException(HttpStatus.CONFLICT, code, message);
    }
    private static JudgingApiException unavailable(String code, String message) {
        return new JudgingApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    private record BeginDeployment(EnvironmentRow environment, Deployment ready, long rowVersion) {}
    private record CalibrationStart(String calibrationId, EnvironmentRow environment) {}
}
