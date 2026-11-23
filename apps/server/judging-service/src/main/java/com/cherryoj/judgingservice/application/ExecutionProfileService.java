package com.cherryoj.judgingservice.application;

import com.cherryoj.judgingservice.formal.FormalProperties;
import com.cherryoj.judgingservice.judge.LanguageCatalog;
import com.cherryoj.judgingservice.limits.ResolvedLanguageLimits;
import com.cherryoj.judgingservice.problem.ProblemTestData;
import com.cherryoj.judgingservice.problem.ProblemTestDataClient;
import com.cherryoj.judgingservice.problem.ProblemTestDataException;
import com.cherryoj.judgingservice.trial.CustomRunProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExecutionProfileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionProfileService.class);

    private final JudgingReadinessService readiness;
    private final FormalProperties budgets;
    private final ProblemTestDataClient testData;
    private final LanguageCatalog languages;
    private final CustomRunProperties customRun;

    public ExecutionProfileService(
            JudgingReadinessService readiness,
            FormalProperties budgets,
            ProblemTestDataClient testData,
            LanguageCatalog languages,
            CustomRunProperties customRun) {
        this.readiness = readiness;
        this.budgets = budgets;
        this.testData = testData;
        this.languages = languages;
        this.customRun = customRun;
    }

    public ExecutionProfile resolve(String problemId, String languageId, Purpose purpose, String trace) {
        ProblemTestData data = currentTestData(problemId, trace);
        ResolvedLanguageLimits limits =
                readiness.readyLimits(problemId, languageId, data.digest(), data.maxTestcaseIoBytes());
        if (limits == null) throw new Rejected(Reason.NOT_READY);

        boolean trial = purpose == Purpose.TRIAL;
        long budget;
        try {
            budget = budgets.executionBudget(
                            languages.requiresCompilation(languageId),
                            limits.cpuNs(),
                            limits.clockNs(),
                            trial ? 1 : data.testcaseCount())
                    .toNanos();
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            throw new Rejected(Reason.NOT_READY);
        }
        if (trial && budget > customRun.maxExecutionBudget().toNanos()) {
            throw new Rejected(Reason.TRIAL_BUDGET_EXCEEDED);
        }
        if (!trial && budget >= budgets.deadline().toNanos()) throw new Rejected(Reason.NOT_READY);
        return new ExecutionProfile(limits, budget);
    }

    private ProblemTestData currentTestData(String problemId, String trace) {
        try {
            return testData.current(problemId, trace);
        } catch (ProblemTestDataException error) {
            LOGGER.atWarn()
                    .addKeyValue("event", "judging.profile.test_data_unavailable")
                    .addKeyValue("problemId", problemId)
                    .addKeyValue("reason", error.code())
                    .log("judging.profile.test_data_unavailable");
            throw new Rejected(Reason.NOT_READY);
        }
    }

    public record ExecutionProfile(ResolvedLanguageLimits limits, long executionBudgetNs) {}

    public enum Purpose {
        FORMAL,
        TRIAL
    }

    public enum Reason {
        NOT_READY,
        TRIAL_BUDGET_EXCEEDED
    }

    public static final class Rejected extends RuntimeException {
        private final Reason reason;

        Rejected(Reason reason) {
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }
}
