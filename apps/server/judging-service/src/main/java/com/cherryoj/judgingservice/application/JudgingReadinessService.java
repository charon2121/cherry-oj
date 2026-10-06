package com.cherryoj.judgingservice.application;

import com.cherryoj.judgingservice.api.JudgingApiException;
import com.cherryoj.judgingservice.api.JudgingDtos;
import com.cherryoj.judgingservice.api.JudgingDtos.BenchmarkSummary;
import com.cherryoj.judgingservice.api.JudgingDtos.Calibration;
import com.cherryoj.judgingservice.api.JudgingDtos.CalibrationRequest;
import com.cherryoj.judgingservice.api.JudgingDtos.ExecutionProfile;
import com.cherryoj.judgingservice.api.JudgingDtos.Readiness;
import com.cherryoj.judgingservice.api.JudgingDtos.ReadinessCheck;
import com.cherryoj.judgingservice.domain.UuidV7;
import com.cherryoj.judgingservice.judge.JudgeGateway;
import com.cherryoj.judgingservice.judge.JudgeGateway.JudgeCallException;
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

    public JudgingReadinessService(JudgingRepository repository, JudgeGateway judge, UuidV7 ids, Clock clock,
                                   ObjectMapper json, PlatformTransactionManager transactionManager,
                                   JudgeNodeRepository nodes) {
        this.repository = repository;
        this.nodes = nodes;
        this.judge = judge;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
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
                    start.calibrationId(), request.problemId(), request.testDataLocation(),
                    request.languageId(), request.referenceSource(),
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
        if (!"AC".equals(verdict)) {
            finishFailedCalibration(start.calibrationId(), summary, "REFERENCE_" + verdict, actorId, traceId);
            return mapCalibration(repository.findCalibration(start.calibrationId()));
        }
        // 判题读到的不是请求里的那份数据：标定中途数据被替换，这次标定对应不上任何一份数据，作废。
        if (!request.testDataDigest().equals(result.testDataDigest())) {
            finishFailedCalibration(start.calibrationId(), summary, "TEST_DATA_CHANGED", actorId, traceId);
            return mapCalibration(repository.findCalibration(start.calibrationId()));
        }

        return transactions.execute(status -> finishValidCalibration(
                start, request, summary, actorId, traceId));
    }

    public Readiness readiness(String problemId, String languageId, String testDataDigest) {
        TransactionTemplate read = new TransactionTemplate(transactions.getTransactionManager());
        read.setReadOnly(true);
        return read.execute(status -> resolveReadiness(problemId, languageId, testDataDigest));
    }

    private CalibrationStart startCalibration(CalibrationRequest request, String actorId, String traceId) {
        var node = requireOnlineNode(request.languageId());
        String id = ids.next().toString();
        repository.insertCalibration(id, request.problemId(), request.languageId(), request.testDataDigest(),
                request.cpuNs(), request.memoryBytes(), request.clockNs(), now());
        audit("CALIBRATION", id, actorId, "CALIBRATION_STARTED", traceId,
                Map.of("calibrationId", id, "problemId", request.problemId(),
                        "testDataDigest", request.testDataDigest(), "nodeId", node.nodeId(),
                        "languageId", request.languageId(), "cpuNs", request.cpuNs(),
                        "memoryBytes", request.memoryBytes()));
        return new CalibrationStart(id, node.endpoint());
    }

    private Calibration finishValidCalibration(CalibrationStart start, CalibrationRequest request,
                                               BenchmarkSummary summary, String actorId, String traceId) {
        requireOnlineNode(request.languageId());
        CalibrationRow previous = repository.findValid(request.problemId(), request.languageId(), true);
        if (previous != null && repository.supersede(previous.id(), now(), previous.rowVersion()) != 1) {
            throw conflict("CALIBRATION_STATE_CONFLICT", "有效校准已经改变，请重试。");
        }
        String previousId = previous == null ? null : previous.id();
        if (repository.markCalibrationValid(start.calibrationId(), actorId, previousId, writeJson(summary), now()) != 1) {
            throw conflict("CALIBRATION_STATE_CONFLICT", "校准状态已经改变，请重试。");
        }
        audit("CALIBRATION", start.calibrationId(), actorId, "CALIBRATION_VALIDATED", traceId,
                Map.of("calibrationId", start.calibrationId(), "problemId", request.problemId(),
                        "languageId", request.languageId(),
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

    /** 题目当前的测试数据指纹 = 标定时的指纹，旧标定才算数；数据一换，标定即过期。 */
    private Readiness resolveReadiness(String problemId, String languageId, String testDataDigest) {
        ArrayList<ReadinessCheck> checks = new ArrayList<>();
        boolean online = !nodes.online(now()).isEmpty();
        checks.add(check("ONLINE_JUDGE_NODE", online, online ? "在线判题节点可用。" : "当前没有在线判题节点，请启动节点并等待注册。"));
        boolean language = !nodes.online(languageId, now()).isEmpty();
        checks.add(check("LANGUAGE", language,
                language ? "有在线节点支持该语言。" : "没有在线节点支持该语言。"));
        CalibrationRow calibration = repository.findValid(problemId, languageId, false);
        boolean current = calibration != null && testDataDigest.equals(calibration.testDataDigest());
        checks.add(check("CALIBRATION", current,
                current ? "该语言存在对应当前测试数据的 VALID 校准。"
                        : calibration == null ? "该语言缺少 VALID 校准。" : "测试数据已更新，该语言的校准已过期，请重新校准。"));
        boolean ready = checks.stream().allMatch(ReadinessCheck::passed);
        ExecutionProfile profile = ready ? new ExecutionProfile(calibration.id(),
                calibration.cpuNs(), calibration.memoryBytes(), calibration.clockNs()) : null;
        return new Readiness(ready, checks, profile);
    }

    /** 任何在线且声明了该语言的节点都行：它们按同一个地址读同一份测试数据。 */
    private JudgeNodeRepository.Node requireOnlineNode(String languageId) {
        var online = nodes.online(languageId, now());
        if (online.isEmpty()) throw noOnline();
        return online.getFirst();
    }

    public static JudgingApiException noOnline() {
        return new JudgingApiException(HttpStatus.SERVICE_UNAVAILABLE, "NO_ONLINE_JUDGE_NODE",
                "当前没有在线判题节点，请启动节点并等待注册后重试。");
    }

    private void audit(String type, String aggregateId, String actorId, String action,
                       String traceId, Map<String, Object> detail) {
        repository.insertAudit(ids.next().toString(), type, aggregateId, actorId, action,
                safeTrace(traceId), writeJson(detail), now());
    }

    private Calibration mapCalibration(CalibrationRow row) {
        BenchmarkSummary summary = row.benchmarkSummaryJson() == null ? null
                : json.readValue(row.benchmarkSummaryJson(), BenchmarkSummary.class);
        return new Calibration(row.id(), row.problemId(), row.languageId(), row.status(), row.cpuNs(), row.memoryBytes(), row.clockNs(), row.testDataDigest(), summary, row.errorMessage(),
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

    private record CalibrationStart(String calibrationId, String endpoint) {}
}
