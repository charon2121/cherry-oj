package com.cherryoj.submissionservice.application;

import com.cherryoj.submissionservice.api.SubmissionDtos.*;
import com.cherryoj.submissionservice.api.SubmissionException;
import com.cherryoj.submissionservice.integration.SubmissionPrerequisites;
import com.cherryoj.submissionservice.judging.ExecutionProfile;
import com.cherryoj.submissionservice.judging.JudgeInput;
import com.cherryoj.submissionservice.judging.ProblemJudgeSnapshot;
import com.cherryoj.submissionservice.persistence.SubmissionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class SubmissionService {
    private final SubmissionMapper store;
    private final SubmissionPrerequisites prerequisites;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final boolean accepting, messaging;

    public SubmissionService(
            SubmissionMapper store,
            SubmissionPrerequisites prerequisites,
            ObjectMapper json,
            PlatformTransactionManager manager,
            @Value("${cherry.submission.accepting:false}") boolean accepting,
            @Value("${cherry.submission.messaging-enabled:false}") boolean messaging) {
        this.store = store;
        this.prerequisites = prerequisites;
        this.json = json;
        this.transaction = new TransactionTemplate(manager);
        this.accepting = accepting;
        this.messaging = messaging;
    }

    public Created create(String user, String key, Create request) {
        UUID.fromString(key);
        // 这里只认语言编号的格式；题目支不支持这门语言由 problem-service 在快照里回答。
        if (request.languageId() == null || !request.languageId().matches("^[a-z][a-z0-9-]{0,31}$"))
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_LANGUAGE", "语言编号无效。");
        String userSource = request.source();
        if (userSource == null || userSource.isBlank())
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_SOURCE", "请输入完整代码。");
        if (userSource.getBytes(StandardCharsets.UTF_8).length > 262144)
            throw problem(HttpStatus.PAYLOAD_TOO_LARGE, "SOURCE_TOO_LARGE", "代码超过 256 KiB。");
        String digest = sha256(json.writeValueAsString(request));
        var replay = replay(user, key, digest);
        if (replay != null) return replay;
        if (!accepting || !messaging) throw problem(HttpStatus.SERVICE_UNAVAILABLE, "SUBMISSIONS_PAUSED", "正式提交暂时不可用。");
        ProblemJudgeSnapshot snapshot =
                prerequisites.resolveProblemJudgeSnapshot(request.problemId().toString(), request.languageId());
        validateProblemJudgeSnapshot(snapshot, request);
        ExecutionProfile executionProfile = prerequisites.resolveExecutionProfile(snapshot);
        validateExecutionProfile(executionProfile, snapshot);
        Instant now = Instant.now();
        String id = uuid7(), event = uuid7();
        var view = new View(
                id,
                snapshot.problemId(),
                snapshot.problemTitle(),
                request.languageId(),
                "PENDING",
                now,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        // Submission 保存用户原文；JudgeInput 冻结实际送判的文件：CORE 是驱动文件 + 用户文件（前缀 + 用户代码）与前缀行数。
        JudgeSources judgeSources = JudgeSources.of(snapshot, userSource);
        String mainFileSource = judgeSources.mainFileSource();
        String solutionFileSource = judgeSources.solutionFileSource();
        var judgeInput = new JudgeInput(
                id,
                "2",
                snapshot.problemId(),
                request.languageId(),
                mainFileSource,
                sha256(mainFileSource),
                solutionFileSource,
                solutionFileSource == null ? null : sha256(solutionFileSource),
                judgeSources.solutionPrefixLineCount(),
                executionProfile.languageCalibrationId(),
                executionProfile.effectiveLimits(),
                executionProfile.limitSource(),
                executionProfile.limitDerivation(),
                now);
        String payload = json.writeValueAsString(Map.of(
                "eventId",
                event,
                "eventType",
                "JudgeRequested",
                "eventVersion",
                1,
                "occurredAt",
                now.toString(),
                "traceId",
                traceId(),
                "aggregateId",
                id,
                "payload",
                Map.of("submissionId", id, "judgeInputContractVersion", "2")));
        try {
            return transaction.execute(status -> {
                // 唯一键冲突会回滚整个事务；不能在这个事务里吞异常后继续提交半条事实。
                var time = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
                store.put(id, user, snapshot.problemId(), json.writeValueAsString(view), userSource, time);
                store.putJudgeInput(id, json.writeValueAsString(judgeInput));
                store.putRequest(user, key, digest, id, time);
                store.putOutbox(event, id, payload, traceParent(), time);
                return new Created(view, true);
            });
        } catch (DuplicateKeyException conflict) {
            var existing = replay(user, key, digest);
            if (existing != null) return existing;
            throw problem(HttpStatus.CONFLICT, "SUBMISSION_CONFLICT", "提交请求发生冲突，请确认原请求。");
        }
    }

    private Created replay(String user, String key, String digest) {
        var request = store.request(user, key);
        if (request == null) return null;
        if (!request.requestDigest().equals(digest))
            throw problem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "该请求编号已用于不同的提交内容。");
        return new Created(get(user, request.submissionId()), false);
    }

    public View get(String user, String id) {
        var row = store.get(id);
        if (row == null || !row.userId().equals(user)) throw missing();
        return json.readValue(row.readModel(), View.class);
    }

    public View request(String user, String key) {
        var request = store.request(user, key);
        if (request == null) throw missing();
        return get(user, request.submissionId());
    }

    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public HistoryPage history(String user, String problemId, int page, int size, String verdict) {
        if (page < 1
                || size < 1
                || size > 100
                || (verdict != null
                        && !java.util.Set.of("AC", "WA", "PE", "CE", "RE", "TLE", "MLE", "OLE", "SE")
                                .contains(verdict))) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_HISTORY_QUERY", "提交记录查询条件不合法。");
        }
        long total = store.historyCount(user, problemId, verdict);
        var items = store.history(user, problemId, verdict, size, ((long) page - 1) * size).stream()
                .map(model -> json.readValue(model, View.class))
                .toList();
        return new HistoryPage(items, page, size, total, (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size));
    }

    public SubmissionSource source(String user, String id) {
        var row = store.source(user, id);
        if (row == null) throw missing();
        var view = json.readValue(row.readModel(), View.class);
        return new SubmissionSource(row.id(), row.problemId(), view.languageId(), row.source());
    }

    public JudgeInput getJudgeInput(String submissionId) {
        String judgeInputJson = store.getJudgeInputJson(submissionId);
        if (judgeInputJson == null) throw missing();
        return json.readValue(judgeInputJson, JudgeInput.class);
    }

    private static void validateProblemJudgeSnapshot(ProblemJudgeSnapshot snapshot, Create request) {
        if (snapshot == null
                || !request.problemId().toString().equals(snapshot.problemId())
                || !request.languageId().equals(snapshot.languageId())
                || !JudgeSources.consistent(snapshot)
                || snapshot.problemTitle() == null) throw invalidJudgePrerequisite();
    }

    private static void validateExecutionProfile(ExecutionProfile executionProfile, ProblemJudgeSnapshot snapshot) {
        if (executionProfile == null
                || !snapshot.problemId().equals(executionProfile.problemId())
                || !snapshot.languageId().equals(executionProfile.languageId())
                || executionProfile.languageCalibrationId() == null
                || executionProfile.effectiveLimits() == null
                || !validLimitSource(executionProfile)) throw invalidJudgePrerequisite();
        var limits = executionProfile.effectiveLimits();
        if (executionProfile.executionBudgetNs() <= 0
                || limits.cpuNs() <= 0
                || limits.memoryBytes() <= 0
                || (limits.clockNs() != null && limits.clockNs() <= 0)) throw invalidJudgePrerequisite();
        try {
            UUID.fromString(executionProfile.languageCalibrationId());
        } catch (IllegalArgumentException error) {
            throw invalidJudgePrerequisite();
        }
    }

    /** 推导依据只在 DERIVED 时出现（docs/protocols/limits.md 第 6 节）；对不上就不冻结，免得记下无法追溯的限制。 */
    private static boolean validLimitSource(ExecutionProfile executionProfile) {
        if (executionProfile.limitSource() == null) return false;
        return switch (executionProfile.limitSource()) {
            case "BASE", "OVERRIDE" -> executionProfile.limitDerivation() == null;
            case "DERIVED" -> executionProfile.limitDerivation() != null;
            default -> false;
        };
    }

    private static SubmissionException invalidJudgePrerequisite() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "INVALID_JUDGE_SNAPSHOT", "当前题目暂时无法判题。");
    }

    private static SubmissionException missing() {
        return problem(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "记录不存在或无权查看。");
    }

    public static SubmissionException problem(HttpStatus status, String code, String message) {
        return new SubmissionException(status, code, message);
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public static String uuid7() {
        var random = UUID.randomUUID();
        return new UUID(
                        (System.currentTimeMillis() << 16) | 0x7000L | (random.getMostSignificantBits() & 0xfffL),
                        (random.getLeastSignificantBits() & 0x3fffffffffffffffL) | 0x8000000000000000L)
                .toString();
    }

    private static String traceParent() {
        String trace = org.slf4j.MDC.get("traceId"), span = org.slf4j.MDC.get("spanId");
        return trace != null
                        && trace.matches("[a-f0-9]{32}")
                        && !trace.equals("0".repeat(32))
                        && span != null
                        && span.matches("[a-f0-9]{16}")
                        && !span.equals("0".repeat(16))
                ? "00-" + trace + "-" + span + "-01"
                : null;
    }

    private static String traceId() {
        String trace = org.slf4j.MDC.get("traceId");
        return trace != null && trace.matches("[a-f0-9]{32}")
                ? trace
                : UUID.randomUUID().toString().replace("-", "");
    }
}
