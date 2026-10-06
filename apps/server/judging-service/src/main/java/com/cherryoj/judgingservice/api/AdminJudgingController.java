package com.cherryoj.judgingservice.api;

import com.cherryoj.judgingservice.application.JudgingReadinessService;
import com.cherryoj.judgingservice.security.CurrentIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

    public AdminJudgingController(JudgingReadinessService service) {
        this.service = service;
    }

    @PostMapping("/calibrations")
    ResponseEntity<JudgingDtos.Calibration> calibrate(
            @Valid @RequestBody JudgingDtos.CalibrationRequest request,
            @RequestHeader(name = "traceparent", required = false) String traceId,
            JwtAuthenticationToken authentication) {
        return noStore(service.calibrate(request, CurrentIdentity.from(authentication).userId(), traceId));
    }

    @GetMapping("/readiness")
    ResponseEntity<JudgingDtos.Readiness> readiness(
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN) String problemId,
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.LANGUAGE_PATTERN) String languageId,
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.SHA_PATTERN) String testDataDigest) {
        return noStore(service.readiness(problemId, languageId, testDataDigest));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
