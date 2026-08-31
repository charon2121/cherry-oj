package com.cherryoj.judgingservice.api;

import com.cherryoj.judgingservice.application.JudgingReadinessService;
import com.cherryoj.judgingservice.config.JudgingProperties;
import com.cherryoj.judgingservice.security.CurrentIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/internal/admin")
public class AdminJudgingController {
    private final JudgingReadinessService service;
    private final JudgingProperties properties;

    public AdminJudgingController(JudgingReadinessService service, JudgingProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping(path = "/deployments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<JudgingDtos.Deployment> deploy(
            @Valid @RequestPart("metadata") JudgingDtos.DeploymentMetadata metadata,
            @RequestPart("archive") MultipartFile archive,
            @RequestHeader(name = "traceparent", required = false) String traceId,
            JwtAuthenticationToken authentication) throws IOException {
        if (archive.isEmpty()) throw new JudgingApiException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_ARCHIVE", "测试数据 ZIP 不能为空。");
        if (archive.getSize() > properties.maxArchiveBytes()) throw new JudgingApiException(
                org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "测试数据 ZIP 超过安全限额。");
        try (var stream = archive.getInputStream()) {
            return noStore(service.deploy(metadata, stream,
                    CurrentIdentity.from(authentication).userId(), traceId));
        }
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
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN) String problemVersionId,
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN) String testDataVersionId,
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.SHA_PATTERN) String expectedSha256,
            @RequestParam @NotBlank @Pattern(regexp = JudgingDtos.LANGUAGE_PATTERN) String languageId) {
        return noStore(service.readiness(problemVersionId, testDataVersionId, expectedSha256, languageId));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
