package com.cherryoj.problemservice.api;

import com.cherryoj.problemservice.api.AdminProblemDtos.CalibrateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.RowVersionRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.UpdateProblemRequest;
import com.cherryoj.problemservice.application.AdminProblemService;
import com.cherryoj.problemservice.application.ProblemPublicationService;
import com.cherryoj.problemservice.security.CurrentIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/admin/problems")
public class AdminProblemController {

    private static final String ID = AdminProblemDtos.UUID_PATTERN;

    private final AdminProblemService problems;
    private final ProblemPublicationService publication;

    public AdminProblemController(AdminProblemService problems, ProblemPublicationService publication) {
        this.problems = problems;
        this.publication = publication;
    }

    @GetMapping
    AdminProblemDtos.ProblemPage list(
            @RequestParam(required = false) @Size(min = 1, max = 100) String q,
            @RequestParam(required = false) ProblemStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return problems.list(q, status, page, size);
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    AdminProblemDtos.Problem create(
            @Valid @RequestBody CreateProblemRequest request,
            JwtAuthenticationToken authentication) {
        return problems.create(request, actor(authentication));
    }

    @GetMapping("/{problemId}")
    ResponseEntity<AdminProblemDtos.Problem> get(@PathVariable @Pattern(regexp = ID) String problemId) {
        return noStore(problems.getProblem(problemId));
    }

    @PatchMapping("/{problemId}")
    AdminProblemDtos.Problem update(
            @PathVariable @Pattern(regexp = ID) String problemId,
            @Valid @RequestBody UpdateProblemRequest request,
            JwtAuthenticationToken authentication) {
        return problems.update(problemId, request, actor(authentication));
    }

    @DeleteMapping("/{problemId}")
    ResponseEntity<Void> delete(
            @PathVariable @Pattern(regexp = ID) String problemId,
            @RequestParam @Min(0) long rowVersion) {
        problems.delete(problemId, rowVersion);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{problemId}/archive")
    AdminProblemDtos.Problem archive(
            @PathVariable @Pattern(regexp = ID) String problemId,
            @Valid @RequestBody RowVersionRequest request,
            JwtAuthenticationToken authentication) {
        return problems.archive(problemId, request.rowVersion(), actor(authentication));
    }

    @GetMapping("/{problemId}/preview")
    ResponseEntity<PublicProblemDtos.ProblemDetail> preview(@PathVariable @Pattern(regexp = ID) String problemId) {
        return noStore(problems.preview(problemId));
    }

    @PostMapping("/{problemId}/calibration")
    ResponseEntity<AdminProblemDtos.LanguageCalibration> calibrate(
            @PathVariable @Pattern(regexp = ID) String problemId,
            @Valid @RequestBody CalibrateProblemRequest request,
            @RequestHeader(name = "traceparent", required = false) String traceparent,
            JwtAuthenticationToken authentication) {
        return noStore(publication.calibrate(problemId, request,
                token(authentication), traceparent, actor(authentication)));
    }

    @GetMapping("/{problemId}/publish-check")
    ResponseEntity<AdminProblemDtos.PublishCheck> publishCheck(
            @PathVariable @Pattern(regexp = ID) String problemId,
            @RequestHeader(name = "traceparent", required = false) String traceparent,
            JwtAuthenticationToken authentication) {
        return noStore(publication.publishCheck(problemId, token(authentication), traceparent));
    }

    @PostMapping("/{problemId}/publish")
    ResponseEntity<AdminProblemDtos.Problem> publish(
            @PathVariable @Pattern(regexp = ID) String problemId,
            @Valid @RequestBody RowVersionRequest request,
            @RequestHeader(name = "traceparent", required = false) String traceparent,
            JwtAuthenticationToken authentication) {
        return noStore(publication.publish(problemId, request.rowVersion(),
                token(authentication), traceparent, actor(authentication)));
    }

    @PostMapping("/{problemId}/unpublish")
    ResponseEntity<AdminProblemDtos.Problem> unpublish(
            @PathVariable @Pattern(regexp = ID) String problemId,
            @Valid @RequestBody RowVersionRequest request,
            JwtAuthenticationToken authentication) {
        return noStore(publication.unpublish(problemId, request.rowVersion(), actor(authentication)));
    }

    private static <T> ResponseEntity<T> noStore(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }

    private static String actor(JwtAuthenticationToken authentication) {
        return CurrentIdentity.from(authentication).userId();
    }

    private static String token(JwtAuthenticationToken authentication) {
        return authentication.getToken().getTokenValue();
    }
}
