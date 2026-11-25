package com.cherryoj.problemservice.api;

import com.cherryoj.identity.resource.CurrentCall;
import com.cherryoj.problemservice.application.AuthoringService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@Validated
@RequestMapping("/internal/admin/problems/{problemId}/authoring/jobs")
public class AuthoringController {
    private static final String ID = AdminProblemDtos.UUID_PATTERN;
    private static final int MAX_BYTES = 8 << 20;

    private final AuthoringService jobs;
    private final ObjectMapper json;

    AuthoringController(AuthoringService jobs, ObjectMapper json) {
        this.jobs = jobs;
        this.json = json;
    }

    @GetMapping
    ResponseEntity<AuthoringService.JobList> list(@PathVariable @Pattern(regexp = ID) String problemId) {
        return reply(HttpStatus.OK, jobs.list(problemId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AuthoringService.JobDetail> create(
            @PathVariable @Pattern(regexp = ID) String problemId, HttpServletRequest request) {
        return reply(HttpStatus.ACCEPTED, jobs.create(problemId, read(request), actor()));
    }

    @GetMapping("/{jobId}")
    ResponseEntity<AuthoringService.JobDetail> get(
            @PathVariable @Pattern(regexp = ID) String problemId, @PathVariable @Pattern(regexp = ID) String jobId) {
        return reply(HttpStatus.OK, jobs.get(problemId, jobId));
    }

    @GetMapping(value = "/{jobId}/report", produces = "text/plain;charset=UTF-8")
    ResponseEntity<String> report(
            @PathVariable @Pattern(regexp = ID) String problemId, @PathVariable @Pattern(regexp = ID) String jobId) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .body(jobs.report(problemId, jobId));
    }

    @PostMapping("/{jobId}/retry")
    ResponseEntity<AuthoringService.JobDetail> retry(
            @PathVariable @Pattern(regexp = ID) String problemId, @PathVariable @Pattern(regexp = ID) String jobId) {
        return reply(HttpStatus.ACCEPTED, jobs.retry(problemId, jobId, actor()));
    }

    @PostMapping("/{jobId}/cancel")
    ResponseEntity<AuthoringService.JobDetail> cancel(
            @PathVariable @Pattern(regexp = ID) String problemId, @PathVariable @Pattern(regexp = ID) String jobId) {
        return reply(HttpStatus.OK, jobs.cancel(problemId, jobId, actor()));
    }

    @PostMapping(value = "/{jobId}/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TestDataDtos.TestData> apply(
            @PathVariable @Pattern(regexp = ID) String problemId,
            @PathVariable @Pattern(regexp = ID) String jobId,
            HttpServletRequest request) {
        return reply(HttpStatus.OK, jobs.apply(problemId, jobId, read(request), actor()));
    }

    private JsonNode read(HttpServletRequest request) {
        byte[] bytes;
        try {
            bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
        } catch (IOException error) {
            throw malformed();
        }
        if (bytes.length > MAX_BYTES) {
            throw new ProblemApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "出题输入超过大小上限。");
        }
        try {
            JsonNode result = json.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                    .readTree(bytes);
            if (result == null || !result.isObject()) throw malformed();
            return result;
        } catch (tools.jackson.core.JacksonException error) {
            throw malformed();
        }
    }

    private static ProblemApiException malformed() {
        return new ProblemApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "出题请求需要一个 JSON 对象。");
    }

    private static String actor() {
        return CurrentCall.requireUser().id();
    }

    private static <T> ResponseEntity<T> reply(HttpStatus code, T response) {
        return ResponseEntity.status(code).cacheControl(CacheControl.noStore()).body(response);
    }
}
