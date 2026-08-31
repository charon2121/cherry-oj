package com.cherryoj.problemservice.api;

import com.cherryoj.problemservice.api.AdminProblemDtos.CreateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateRevisionRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.RowVersionRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.UpdateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.UpdateVersionRequest;
import com.cherryoj.problemservice.application.AdminProblemService;
import com.cherryoj.problemservice.application.ProblemPublicationService;
import com.cherryoj.problemservice.security.CurrentIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    AdminProblemDtos.Problem get(@PathVariable String problemId) {
        return problems.getProblem(problemId);
    }

    @PatchMapping("/{problemId}")
    AdminProblemDtos.Problem update(
            @PathVariable String problemId,
            @Valid @RequestBody UpdateProblemRequest request,
            JwtAuthenticationToken authentication) {
        return problems.updateProblem(problemId, request, actor(authentication));
    }

    @PostMapping("/{problemId}/archive")
    AdminProblemDtos.Problem archive(
            @PathVariable String problemId,
            @Valid @RequestBody RowVersionRequest request,
            JwtAuthenticationToken authentication) {
        return problems.archive(problemId, request.rowVersion(), actor(authentication));
    }

    @PostMapping("/{problemId}/versions")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    AdminProblemDtos.Version createRevision(
            @PathVariable String problemId,
            @Valid @RequestBody CreateRevisionRequest request,
            JwtAuthenticationToken authentication) {
        return problems.createRevision(problemId, request, actor(authentication));
    }

    @GetMapping("/{problemId}/versions/{versionId}")
    AdminProblemDtos.Version getVersion(@PathVariable String problemId, @PathVariable String versionId) {
        return problems.getVersion(problemId, versionId);
    }

    @PatchMapping("/{problemId}/versions/{versionId}")
    AdminProblemDtos.Version updateVersion(
            @PathVariable String problemId,
            @PathVariable String versionId,
            @Valid @RequestBody UpdateVersionRequest request,
            JwtAuthenticationToken authentication) {
        return problems.updateVersion(problemId, versionId, request, actor(authentication));
    }

    @DeleteMapping("/{problemId}/versions/{versionId}")
    ResponseEntity<Void> deleteVersion(
            @PathVariable String problemId,
            @PathVariable String versionId,
            @RequestParam @Min(0) long rowVersion,
            JwtAuthenticationToken authentication) {
        problems.deleteDraft(problemId, versionId, rowVersion, actor(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{problemId}/versions/{versionId}/preview")
    ResponseEntity<PublicProblemDtos.ProblemDetail> preview(
            @PathVariable String problemId, @PathVariable String versionId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(problems.preview(problemId, versionId));
    }

    @PostMapping("/{problemId}/versions/{versionId}/deployment")
    ResponseEntity<AdminProblemDtos.TestDataDeployment> deploy(
            @PathVariable String problemId,
            @PathVariable String versionId,
            @Valid @RequestBody AdminProblemDtos.DeployTestDataRequest request,
            @RequestHeader(name = "traceparent", required = false) String traceparent,
            JwtAuthenticationToken authentication) {
        return noStore(publication.deploy(problemId, versionId, request,
                token(authentication), traceparent, actor(authentication)));
    }

    @PostMapping("/{problemId}/versions/{versionId}/calibration")
    ResponseEntity<AdminProblemDtos.LanguageCalibration> calibrate(
            @PathVariable String problemId,
            @PathVariable String versionId,
            @Valid @RequestBody AdminProblemDtos.CalibrateProblemRequest request,
            @RequestHeader(name = "traceparent", required = false) String traceparent,
            JwtAuthenticationToken authentication) {
        return noStore(publication.calibrate(problemId, versionId, request,
                token(authentication), traceparent, actor(authentication)));
    }

    @GetMapping("/{problemId}/versions/{versionId}/publish-check")
    ResponseEntity<AdminProblemDtos.PublishCheck> publishCheck(
            @PathVariable String problemId,
            @PathVariable String versionId,
            @RequestHeader(name = "traceparent", required = false) String traceparent,
            JwtAuthenticationToken authentication) {
        return noStore(publication.publishCheck(
                problemId, versionId, token(authentication), traceparent));
    }

    @PostMapping("/{problemId}/versions/{versionId}/publish")
    ResponseEntity<AdminProblemDtos.Version> publish(
            @PathVariable String problemId,
            @PathVariable String versionId,
            @Valid @RequestBody AdminProblemDtos.PublishProblemRequest request,
            @RequestHeader(name = "traceparent", required = false) String traceparent,
            JwtAuthenticationToken authentication) {
        return noStore(publication.publish(problemId, versionId, request.rowVersion(),
                token(authentication), traceparent, actor(authentication)));
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
