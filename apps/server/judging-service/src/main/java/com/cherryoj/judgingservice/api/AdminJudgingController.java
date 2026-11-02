package com.cherryoj.judgingservice.api;

import com.cherryoj.identity.resource.CurrentCall;
import com.cherryoj.judgingservice.application.JudgeNodeRegistry;
import com.cherryoj.judgingservice.application.JudgingReadinessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/admin")
public class AdminJudgingController {
    private final JudgingReadinessService service;
    private final JudgeNodeRegistry nodes;

    public AdminJudgingController(JudgingReadinessService service, JudgeNodeRegistry nodes) {
        this.service = service;
        this.nodes = nodes;
    }

    /** 管理端总览用：已登记判题节点与是否在线。路径在 /internal/admin/** 下，要求 ADMIN 委托身份。 */
    @GetMapping("/judge-nodes")
    ResponseEntity<JudgeNodeDtos.NodeStatusList> judgeNodes() {
        return noStore(nodes.status());
    }

    @PostMapping("/calibrations")
    ResponseEntity<JudgingDtos.Calibration> calibrate(
            @Valid @RequestBody JudgingDtos.CalibrationRequest request,
            @RequestHeader(name = "traceparent", required = false) String traceId) {
        return noStore(service.calibrate(request, CurrentCall.requireUser().id(), traceId));
    }

    @GetMapping("/readiness")
    ResponseEntity<JudgingDtos.Readiness> readiness(
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN) String problemId,
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.LANGUAGE_PATTERN) String languageId,
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.SHA_PATTERN) String testDataDigest,
            @RequestParam @Min(0) long maxTestcaseIoBytes) {
        return noStore(service.readiness(problemId, languageId, testDataDigest, maxTestcaseIoBytes));
    }

    /** languageIds 是 problem-service 给出的题目开放语言：judging-service 不知道题目开放了哪些语言。 */
    @GetMapping("/limits")
    ResponseEntity<JudgingDtos.LanguageLimitsList> limits(
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN) String problemId,
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.SHA_PATTERN) String testDataDigest,
            @RequestParam @Min(0) long maxTestcaseIoBytes,
            @RequestParam @Size(min = 1, max = 32)
                    List<@NotBlank @Pattern(regexp = JudgingDtos.LANGUAGE_PATTERN) String> languageIds) {
        if (new HashSet<>(languageIds).size() != languageIds.size()) {
            throw new JudgingApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_LANGUAGE", "语言列表里有重复项。");
        }
        return noStore(service.limits(problemId, languageIds, testDataDigest, maxTestcaseIoBytes));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
