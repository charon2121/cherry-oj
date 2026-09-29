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
import com.cherryoj.judgingservice.persistence.JudgingRepository.EnvironmentRow;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class JudgingReadinessService {
    private static final int MAX_SAFE_ERROR = 128;
    private final com.cherryoj.judgingservice.persistence.JudgeNodeRepository nodes;
    private final NodeDeploymentService nodeDeployments;
    private final JudgingRepository repository;
    private final JudgeGateway judge;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    public JudgingReadinessService(JudgingRepository repository, JudgeGateway judge, UuidV7 ids, Clock clock,
                                   ObjectMapper json, PlatformTransactionManager transactionManager,
                                   com.cherryoj.judgingservice.persistence.JudgeNodeRepository nodes,
                                   NodeDeploymentService nodeDeployments) {
        this.repository = repository;
        this.nodes = nodes; this.nodeDeployments = nodeDeployments;
        this.judge = judge;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Deployment deploy(DeploymentMetadata metadata, InputStream archive,
                             String actorId, String traceId) {
        return nodeDeployments.deploy(metadata, archive, traceId);
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
            result = judge.judge(start.endpoint(), new JudgeGateway.JudgeRequest(
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
        var node = nodes.ready(environment.id(), request.testDataVersionId(), request.expectedSha256(), now());
        if (node == null) throw NodeDeploymentService.noOnline();
        return new CalibrationStart(id, environment, node.endpoint());
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
        boolean online = environment != null && !nodes.online(environment.id(), now()).isEmpty();
        checks.add(check("ONLINE_JUDGE_NODE", online, online ? "在线判题节点可用。" : "当前没有在线判题节点，请启动节点并等待注册。"));
        var readyNode = environment == null ? null : nodes.ready(environment.id(), testDataVersionId, expectedSha256, now());
        boolean deployed = readyNode != null;
        checks.add(check("DEPLOYMENT", deployed,
                deployed ? "测试数据已按预期摘要部署。" : "测试数据尚未 READY 或摘要不匹配。"));
        CalibrationRow calibration = environment == null ? null
                : repository.findValid(problemVersionId, languageId, environment.id(), false);
        boolean calibrated = calibration != null;
        checks.add(check("CALIBRATION", calibrated,
                calibrated ? "当前环境存在 VALID 校准。" : "当前环境缺少 VALID 校准。"));
        boolean ready = checks.stream().allMatch(ReadinessCheck::passed);
        ExecutionProfile profile = ready ? new ExecutionProfile(environment.id(), environment.fingerprint(),
                readyNode.endpoint(), calibration.id(), calibration.cpuNs(), calibration.memoryBytes(),
                calibration.clockNs()) : null;
        return new Readiness(ready, environment == null ? null : environment.id(), checks, profile);
    }

    private void requireLanguageAndDeployment(EnvironmentRow environment, String languageId,
                                              String testDataVersionId, String expectedSha) {
        if (!repository.languageEnabled(environment.id(), languageId)) {
            throw conflict("LANGUAGE_NOT_ENABLED", "语言未在当前 ACTIVE 环境启用。");
        }
        if (nodes.online(environment.id(), now()).isEmpty()) throw NodeDeploymentService.noOnline();
        if (nodes.ready(environment.id(), testDataVersionId, expectedSha, now()) == null)
            throw conflict("DEPLOYMENT_NOT_READY", "在线节点尚未安装匹配的测试数据，请先部署。");
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
    private static JudgingApiException conflict(String code, String message) {
        return new JudgingApiException(HttpStatus.CONFLICT, code, message);
    }
    private static JudgingApiException unavailable(String code, String message) {
        return new JudgingApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    private record CalibrationStart(String calibrationId, EnvironmentRow environment, String endpoint) {}
}
