package com.cherryoj.judgingservice.api;

import com.cherryoj.judgingservice.application.JudgingReadinessService;
import com.cherryoj.judgingservice.formal.FormalProperties;
import com.cherryoj.judgingservice.problem.ProblemTestDataClient;
import com.cherryoj.judgingservice.problem.ProblemTestDataException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 契约见 contracts/execution-profile.schema.json：按题目×语言解析，测试点数取自题目此刻的测试数据。 */
@RestController
public class SubmissionExecutionProfileController {
    private final JudgingReadinessService service;
    private final FormalProperties budgets;
    private final ProblemTestDataClient testData;

    public SubmissionExecutionProfileController(JudgingReadinessService service, FormalProperties budgets,
                                                ProblemTestDataClient testData) {
        this.service = service; this.budgets = budgets; this.testData = testData;
    }

    @PostMapping("/internal/submission/execution-profile")
    public Profile resolve(@Valid @RequestBody Request request,
                           @RequestHeader(value = "traceparent", required = false) String trace) {
        var data = currentTestData(request.problemId(), trace);
        var readiness = service.readiness(request.problemId(), request.languageId(), data.digest());
        var profile = readiness.executionProfile();
        if (!readiness.ready() || profile == null) throw unavailable();
        boolean trial = "trial".equals(request.purpose());
        long budget;
        try { budget = budgets.executionBudget(profile.cpuNs(), profile.clockNs(), trial ? 1 : data.caseCount()).toNanos(); }
        catch (ArithmeticException invalid) { throw unavailable(); }
        if (trial && budget > java.time.Duration.ofSeconds(45).toNanos()) throw new JudgingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RUN_LIMIT_UNSUPPORTED", "此题目的执行预算超过自测期限。");
        if (!trial && budget >= budgets.deadline().toNanos()) throw unavailable();
        return new Profile(request.problemId(), request.languageId(), profile.calibrationId(),
                new Limits(profile.cpuNs(), profile.memoryBytes(), profile.clockNs()), budget);
    }

    private com.cherryoj.judgingservice.problem.ProblemTestData currentTestData(String problemId, String trace) {
        try { return testData.current(problemId, trace); }
        catch (ProblemTestDataException error) { throw unavailable(); }
    }

    private static JudgingApiException unavailable() {
        return new JudgingApiException(HttpStatus.SERVICE_UNAVAILABLE, "JUDGING_NOT_READY", "当前题目暂时无法判题。");
    }

    public record Request(@NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN) String problemId,
                          @NotBlank @Pattern(regexp = "cpp") String languageId,
                          @Pattern(regexp = "formal|trial") String purpose) {}

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Limits(long cpuNs, long memoryBytes, Long clockNs) {}

    public record Profile(String problemId, String languageId, String languageCalibrationId,
                          Limits effectiveLimits, long executionBudgetNs) {}
}
