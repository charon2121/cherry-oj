package com.cherryoj.problemservice.api;

import com.cherryoj.problemservice.application.SubmissionSnapshotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

@RestController
public class SubmissionSnapshotController {
    private final SubmissionSnapshotService service;
    public SubmissionSnapshotController(SubmissionSnapshotService service) { this.service = service; }
    @PostMapping("/internal/submission/problem-snapshot")
    public SubmissionSnapshotService.Snapshot resolve(@Valid @RequestBody Request request) {
        return service.resolve(request.problemId(), request.languageId());
    }
    public record Request(@NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String problemId,
                          @NotBlank @Pattern(regexp = "cpp") String languageId) {}
}
