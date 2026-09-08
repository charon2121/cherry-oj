package com.cherryoj.judgingservice.api;

import com.cherryoj.judgingservice.application.JudgingReadinessService;
import com.cherryoj.judgingservice.config.JudgeNodeProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class SubmissionExecutionProfileController {
    private final JudgingReadinessService service;
    private final JudgeNodeProperties nodes;
    private final com.cherryoj.judgingservice.formal.FormalProperties budgets;
    public SubmissionExecutionProfileController(JudgingReadinessService service, JudgeNodeProperties nodes, com.cherryoj.judgingservice.formal.FormalProperties budgets) {
        this.service = service; this.nodes = nodes; this.budgets = budgets;
    }
    @PostMapping("/internal/submission/execution-profile")
    public Profile resolve(@Valid @RequestBody Request request) {
        if (!nodes.remote()) throw unavailable();
        var readiness = service.readiness(request.problemVersionId(), request.testDataVersionId(),
                request.testDataContentSha256(), request.languageId());
        var profile = readiness.executionProfile();
        if (!readiness.ready() || profile == null) throw unavailable();
        long budget;
        try { budget=budgets.executionBudget(profile.cpuNs(),profile.clockNs(),"trial".equals(request.purpose())?1:request.totalCount()).toNanos(); }
        catch(ArithmeticException invalid) { throw unavailable(); }
        if("trial".equals(request.purpose()) && budget>java.time.Duration.ofSeconds(45).toNanos()) throw new JudgingApiException(HttpStatus.UNPROCESSABLE_ENTITY,"RUN_LIMIT_UNSUPPORTED","此题目的执行预算超过自测期限。");
        if(!"trial".equals(request.purpose()) && budget>=budgets.deadline().toNanos()) throw unavailable();
        return new Profile(request.problemVersionId(), request.testDataVersionId(), request.languageId(),
                profile.environmentId(), profile.environmentFingerprint(), profile.calibrationId(),
                new Limits(profile.cpuNs(), profile.memoryBytes(), profile.clockNs()),budget);
    }
    private static JudgingApiException unavailable() {
        return new JudgingApiException(HttpStatus.SERVICE_UNAVAILABLE, "JUDGING_NOT_READY", "当前题目暂时无法判题。");
    }
    public record Request(@NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN) String problemVersionId,
                          @NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN) String testDataVersionId,
                          @NotBlank @Pattern(regexp = JudgingDtos.SHA_PATTERN) String testDataContentSha256,
                          @NotBlank @Pattern(regexp = "cpp") String languageId, @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(1000) int totalCount, @Pattern(regexp="formal|trial") String purpose) {
        public Request(String p,String t,String h,String l,int n){this(p,t,h,l,n,null);}
    }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Limits(long cpuNs, long memoryBytes, Long clockNs) {}
    public record Profile(String problemVersionId, String testDataVersionId, String languageId,
                          String judgeEnvironmentId, String environmentFingerprint, String languageCalibrationId,
                          Limits effectiveLimits,long executionBudgetNs) {}
}
