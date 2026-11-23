package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.judge.JudgeGateway;
import com.cherryoj.judgingservice.judge.LanguageCatalog;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import com.cherryoj.judgingservice.problem.ProblemTestData;
import com.cherryoj.judgingservice.problem.ProblemTestDataClient;
import com.cherryoj.judgingservice.problem.ProblemTestDataException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cherry.formal.enabled", havingValue = "true")
class FormalAttemptRunner {
    private final FormalTaskStore store;
    private final FormalInputClient inputs;
    private final JudgeNodeRepository nodes;
    private final JudgeGateway judge;
    private final ProblemTestDataClient testData;
    private final FormalProperties properties;
    private final Clock clock;
    private final LanguageCatalog languages;

    FormalAttemptRunner(
            FormalTaskStore store,
            FormalInputClient inputs,
            JudgeNodeRepository nodes,
            JudgeGateway judge,
            ProblemTestDataClient testData,
            FormalProperties properties,
            Clock clock,
            LanguageCatalog languages) {
        this.store = store;
        this.inputs = inputs;
        this.nodes = nodes;
        this.judge = judge;
        this.testData = testData;
        this.properties = properties;
        this.clock = clock;
        this.languages = languages;
    }

    void execute(FormalTaskStore.Task task) {
        Instant deadline = task.createdAt().toInstant(ZoneOffset.UTC).plus(properties.deadline());
        try {
            if (!clock.instant().isBefore(deadline)) throw new FormalFailure("JUDGE_DEADLINE_EXCEEDED");
            if (task.attemptNo() > properties.maxAttempts()) throw new FormalFailure("JUDGE_ATTEMPTS_EXHAUSTED");
            var input = inputs.get(task.submissionId(), task.traceParent());
            FormalInputValidator.validate(input, task.submissionId(), languages);
            // 地址不在提交时冻结：判题时向 problem-service 取题目此刻的测试数据，预算按当前测试点数重算。
            var data = currentTestData(input.problemId(), task.traceParent());
            Duration budget;
            try {
                budget = properties.executionBudget(
                        languages.requiresCompilation(input.languageId()),
                        input.effectiveLimits().cpuNs(),
                        input.effectiveLimits().clockNs(),
                        data.testcaseCount());
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
            store.finish(
                    task,
                    FormalResultMapper.toLifecycleResult(result, data.testcaseCount(), input, languages),
                    result.testDataDigest(),
                    null,
                    null);
        } catch (Exception error) {
            String code = error instanceof FormalFailure ? error.getMessage() : "JUDGE_EXECUTION_FAILED";
            boolean retry = task.attemptNo() < properties.maxAttempts()
                    && clock.instant().isBefore(deadline)
                    && !Set.of("INVALID_JUDGE_INPUT", "JUDGE_BUDGET_UNAVAILABLE")
                            .contains(code);
            store.finish(task, null, null, code, retry ? Duration.ofSeconds(task.attemptNo() == 1 ? 1 : 5) : null);
        }
    }

    private ProblemTestData currentTestData(String problemId, String trace) {
        try {
            return testData.current(problemId, trace);
        } catch (ProblemTestDataException error) {
            throw new FormalFailure(error.code());
        }
    }
}
