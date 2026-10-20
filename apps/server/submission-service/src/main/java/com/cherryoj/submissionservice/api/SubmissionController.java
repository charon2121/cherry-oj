package com.cherryoj.submissionservice.api;

import com.cherryoj.identity.resource.CurrentCall;
import com.cherryoj.submissionservice.application.SubmissionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class SubmissionController {
    private final SubmissionService service;

    public SubmissionController(SubmissionService service) {
        this.service = service;
    }

    @PostMapping("/api/submissions")
    public ResponseEntity<SubmissionDtos.View> create(
            @Valid @RequestBody SubmissionDtos.Create body, @RequestHeader("Idempotency-Key") UUID key) {
        var result = service.create(userId(), key.toString(), body);
        return ResponseEntity.status(result.fresh() ? 201 : 200)
                .header("Cache-Control", "no-store")
                .body(result.view());
    }

    @GetMapping("/api/submissions")
    public ResponseEntity<SubmissionDtos.HistoryPage> history(
            @RequestParam UUID problemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String verdict) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(service.history(userId(), problemId.toString(), page, size, verdict));
    }

    @GetMapping("/api/submissions/{id}/source")
    public ResponseEntity<SubmissionDtos.Source> source(@PathVariable UUID id) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.source(userId(), id.toString()));
    }

    @GetMapping("/api/submissions/{id}")
    public ResponseEntity<SubmissionDtos.View> get(@PathVariable UUID id) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.get(userId(), id.toString()));
    }

    @GetMapping("/api/submission-requests/{key}")
    public ResponseEntity<SubmissionDtos.View> request(@PathVariable UUID key) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.request(userId(), key.toString()));
    }

    @GetMapping("/internal/judging/judge-inputs/{id}")
    public SubmissionDtos.Input input(@PathVariable UUID id) {
        return service.input(id.toString());
    }

    private static String userId() {
        return CurrentCall.requireUser().id();
    }
}
