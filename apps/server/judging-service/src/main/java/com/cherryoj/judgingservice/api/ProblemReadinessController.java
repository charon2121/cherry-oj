package com.cherryoj.judgingservice.api;

import com.cherryoj.judgingservice.application.JudgingReadinessService;
import jakarta.validation.Valid;
import java.util.HashSet;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProblemReadinessController {
    private final JudgingReadinessService service;

    public ProblemReadinessController(JudgingReadinessService service) {
        this.service = service;
    }

    @PostMapping("/internal/problems/readiness")
    ResponseEntity<JudgingDtos.ProblemReadiness> readiness(
            @Valid @RequestBody JudgingDtos.ProblemReadinessRequest request) {
        if (new HashSet<>(request.languageIds()).size() != request.languageIds().size()) {
            throw new JudgingApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_LANGUAGE", "语言列表里有重复项。");
        }
        var languages = request.languageIds().stream()
                .map(languageId -> {
                    var result = service.readiness(
                            request.problemId(), languageId, request.testDataDigest(), request.maxTestcaseIoBytes());
                    return new JudgingDtos.ProblemLanguageReadiness(languageId, result.ready(), result.checks());
                })
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new JudgingDtos.ProblemReadiness(languages));
    }
}
