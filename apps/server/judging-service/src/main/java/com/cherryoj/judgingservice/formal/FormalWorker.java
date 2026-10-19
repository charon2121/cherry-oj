package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.judge.JudgeDiagnostics;
import com.cherryoj.judgingservice.judge.JudgeGateway;
import com.cherryoj.judgingservice.judge.LanguageFiles;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import com.cherryoj.judgingservice.problem.ProblemTestDataClient;
import com.cherryoj.judgingservice.problem.ProblemTestDataException;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cherry.formal.enabled", havingValue = "true")
public class FormalWorker {
    private final FormalTaskStore store;
    private final FormalInputClient inputs;
    private final JudgeNodeRepository nodes;
    private final JudgeGateway judge;
    private final ProblemTestDataClient testData;
    private final FormalProperties properties;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, FormalTaskStore.Task> active = new ConcurrentHashMap<>();
    private final Semaphore slots;

    public FormalWorker(
            FormalTaskStore store,
            FormalInputClient inputs,
            JudgeNodeRepository nodes,
            JudgeGateway judge,
            ProblemTestDataClient testData,
            FormalProperties properties,
            Clock clock) {
        this.store = store;
        this.inputs = inputs;
        this.nodes = nodes;
        this.judge = judge;
        this.testData = testData;
        this.properties = properties;
        this.clock = clock;
        slots = new Semaphore(properties.parallelism());
    }

    @Scheduled(fixedDelay = 500, scheduler = "formalTaskScheduler")
    public void poll() {
        for (var task : active.values()) store.renew(task, properties.leaseDuration());
        while (slots.tryAcquire()) {
            FormalTaskStore.Task task;
            try {
                task = store.claim(properties.leaseDuration());
            } catch (RuntimeException error) {
                slots.release();
                throw error;
            }
            if (task == null) {
                slots.release();
                return;
            }
            active.put(task.id(), task);
            try {
                executor.submit(() -> {
                    try {
                        execute(task);
                    } finally {
                        active.remove(task.id(), task);
                        slots.release();
                    }
                });
            } catch (RejectedExecutionException stopping) {
                active.remove(task.id(), task);
                slots.release();
                return;
            }
        }
    }

    public void execute(FormalTaskStore.Task task) {
        Instant deadline = task.createdAt().toInstant(ZoneOffset.UTC).plus(properties.deadline());
        try {
            if (!clock.instant().isBefore(deadline)) throw new FormalFailure("JUDGE_DEADLINE_EXCEEDED");
            if (task.attemptNo() > properties.maxAttempts()) throw new FormalFailure("JUDGE_ATTEMPTS_EXHAUSTED");
            var input = inputs.get(task.submissionId(), task.traceParent());
            validate(input, task.submissionId());
            // 地址不在提交时冻结：判题时向 problem-service 取题目此刻的测试数据，预算按当前测试点数重算。
            var data = currentTestData(input.problemId(), task.traceParent());
            Duration budget;
            try {
                budget = properties.executionBudget(
                        input.effectiveLimits().cpuNs(), input.effectiveLimits().clockNs(), data.testcaseCount());
            } catch (ArithmeticException | IllegalArgumentException error) {
                throw new FormalFailure("JUDGE_BUDGET_UNAVAILABLE");
            }
            if (budget.isNegative()
                    || budget.isZero()
                    || clock.instant().plus(budget).isAfter(deadline))
                throw new FormalFailure("JUDGE_BUDGET_UNAVAILABLE");
            // 派发时再选节点：任何在线、声明了该语言的节点都行，它们按同一个地址读同一份数据。
            var online = nodes.online(input.languageId(), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            if (online.isEmpty()) throw new FormalFailure("NO_MATCHING_JUDGE_NODE");
            var result = judge.judge(
                    online.getFirst().endpoint(),
                    new JudgeGateway.JudgeRequest(
                            input.submissionId(),
                            input.problemId(),
                            data.location(),
                            input.languageId(),
                            input.completeSource(),
                            input.solutionSource(),
                            input.effectiveLimits(),
                            "submit"),
                    task.traceParent(),
                    budget);
            if ("SE".equals(result.verdict())) throw new FormalFailure("JUDGE_SYSTEM_ERROR");
            store.finish(task, safe(result, data.testcaseCount(), input), result.testDataDigest(), null, null);
        } catch (Exception error) {
            String code = error instanceof FormalFailure ? error.getMessage() : "JUDGE_EXECUTION_FAILED";
            boolean retry = task.attemptNo() < properties.maxAttempts()
                    && clock.instant().isBefore(deadline)
                    && !Set.of("INVALID_JUDGE_INPUT", "JUDGE_BUDGET_UNAVAILABLE")
                            .contains(code);
            store.finish(task, null, null, code, retry ? Duration.ofSeconds(task.attemptNo() == 1 ? 1 : 5) : null);
        }
    }

    private com.cherryoj.judgingservice.problem.ProblemTestData currentTestData(String problemId, String trace) {
        try {
            return testData.current(problemId, trace);
        } catch (ProblemTestDataException error) {
            throw new FormalFailure(error.code());
        }
    }

    private static void validate(FormalInput input, String id) {
        // CORE 的 completeSource 是驱动文件，上限与题目的驱动代码一致（1 MiB）；用户代码的上限在提交入口检查。
        if (input == null
                || !id.equals(input.submissionId())
                || !"2".equals(input.contractVersion())
                || !"cpp".equals(input.languageId())
                || input.completeSource() == null
                || input.completeSource().getBytes(StandardCharsets.UTF_8).length > 1_048_576
                || input.sourceSha256() == null
                || !hash(input.completeSource()).equals(input.sourceSha256())
                || !validSolution(input)
                || input.effectiveLimits() == null
                || input.effectiveLimits().cpuNs() <= 0
                || input.effectiveLimits().memoryBytes() <= 0
                || (input.effectiveLimits().clockNs() != null
                        && input.effectiveLimits().clockNs() <= 0)
                || input.createdAt() == null) throw new FormalFailure("INVALID_JUDGE_INPUT");
        try {
            for (String uuid : List.of(input.problemId(), input.languageCalibrationId())) UUID.fromString(uuid);
        } catch (RuntimeException error) {
            throw new FormalFailure("INVALID_JUDGE_INPUT");
        }
    }
    /** 用户文件、摘要、前缀行数三者同时出现（CORE）或同时缺席（ACM），且用户文件与摘要一致。 */
    static boolean validSolution(FormalInput input) {
        if (input.solutionSource() == null)
            return input.solutionSha256() == null && input.solutionPrefixLineCount() == null;
        return input.solutionSha256() != null
                && hash(input.solutionSource()).equals(input.solutionSha256())
                && input.solutionPrefixLineCount() != null
                && input.solutionPrefixLineCount() >= 0
                && LanguageFiles.of(input.languageId()) != null
                && input.solutionSource().getBytes(StandardCharsets.UTF_8).length <= 327_680;
    }

    static Map<String, Object> safe(JudgeGateway.JudgeResult result, int testcaseCount, FormalInput input) {
        Set<String> verdicts = Set.of("AC", "WA", "PE", "TLE", "MLE", "OLE", "RE", "CE");
        if (result == null || result.verdict() == null || !verdicts.contains(result.verdict()))
            throw new FormalFailure("INVALID_JUDGE_RESULT");
        var safe = new LinkedHashMap<String, Object>();
        safe.put("verdict", result.verdict());
        if (result.cpuNs() != null) {
            if (result.cpuNs() < 0) throw new FormalFailure("INVALID_JUDGE_RESULT");
            safe.put("cpuNs", result.cpuNs());
        }
        if (result.memoryBytes() != null) {
            if (result.memoryBytes() < 0) throw new FormalFailure("INVALID_JUDGE_RESULT");
            safe.put("memoryBytes", result.memoryBytes());
        }
        if (result.testcaseResults() != null) {
            if (result.testcaseResults().size() > testcaseCount) throw new FormalFailure("INVALID_JUDGE_RESULT");
            int passed = 0, index = 0;
            for (var test : result.testcaseResults()) {
                if (test.idx() != ++index || !verdicts.contains(test.verdict()))
                    throw new FormalFailure("INVALID_JUDGE_RESULT");
                if ("AC".equals(test.verdict())) passed++;
            }
            safe.put("passedTestcaseCount", passed);
            safe.put("executedTestcaseCount", index);
            safe.put("testcaseCount", testcaseCount);
        }
        if ("CE".equals(result.verdict())
                && result.message() != null
                && !result.message().isBlank()) {
            // CORE：用户文件的行号按冻结的前缀行数换算成编辑器行号；路径只留文件名。
            LanguageFiles files = input.solutionSource() == null ? null : LanguageFiles.of(input.languageId());
            String diagnostic = JudgeDiagnostics.forUser(
                    result.message(),
                    files == null ? null : files.solutionFile(),
                    files == null ? null : input.solutionPrefixLineCount(),
                    JudgeDiagnostics.looksCut(result.message()));
            safe.put("message", diagnostic.substring(0, Math.min(diagnostic.length(), 8192)));
        }
        return safe;
    }

    static String hash(String source) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }
}
