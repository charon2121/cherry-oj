package com.cherryoj.problemservice.api;

import com.cherryoj.problemservice.application.ProblemJudgeSnapshotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProblemJudgeSnapshotController {
    private final ProblemJudgeSnapshotService service;

    public ProblemJudgeSnapshotController(ProblemJudgeSnapshotService service) {
        this.service = service;
    }

    @PostMapping("/internal/submission/problem-snapshot")
    public ProblemJudgeSnapshotService.ProblemJudgeSnapshot resolve(@Valid @RequestBody Request request) {
        return service.resolve(request.problemId(), request.languageId());
    }

    public record Request(
            @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String problemId,

            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{0,31}$")
            String languageId) {}
}
