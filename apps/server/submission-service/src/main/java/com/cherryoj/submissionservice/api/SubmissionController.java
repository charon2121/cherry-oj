package com.cherryoj.submissionservice.api;

import com.cherryoj.submissionservice.application.SubmissionService;
import com.cherryoj.submissionservice.security.CurrentIdentity;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class SubmissionController {
    private final SubmissionService service;
    public SubmissionController(SubmissionService service) { this.service=service; }
    @PostMapping("/api/submissions")
    public ResponseEntity<SubmissionDtos.View> create(@Valid @RequestBody SubmissionDtos.Create body,
            @RequestHeader("Idempotency-Key") UUID key, JwtAuthenticationToken identity) {
        var result=service.create(CurrentIdentity.from(identity).userId(),key.toString(),body);
        return ResponseEntity.status(result.fresh()?201:200).header("Cache-Control","no-store").body(result.view());
    }
    @GetMapping("/api/submissions")
    public ResponseEntity<SubmissionDtos.HistoryPage> history(@RequestParam UUID problemId,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String verdict, JwtAuthenticationToken identity) {
        return ResponseEntity.ok().header("Cache-Control","no-store")
                .body(service.history(CurrentIdentity.from(identity).userId(),problemId.toString(),page,size,verdict));
    }
    @GetMapping("/api/submissions/{id}/source")
    public ResponseEntity<SubmissionDtos.Source> source(@PathVariable UUID id, JwtAuthenticationToken identity) {
        return ResponseEntity.ok().header("Cache-Control","no-store")
                .body(service.source(CurrentIdentity.from(identity).userId(),id.toString()));
    }
    @GetMapping("/api/submissions/{id}")
    public ResponseEntity<SubmissionDtos.View> get(@PathVariable UUID id, JwtAuthenticationToken identity) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.get(CurrentIdentity.from(identity).userId(),id.toString()));
    }
    @GetMapping("/api/submission-requests/{key}")
    public ResponseEntity<SubmissionDtos.View> request(@PathVariable UUID key, JwtAuthenticationToken identity) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.request(CurrentIdentity.from(identity).userId(),key.toString()));
    }
    @GetMapping("/internal/judging/judge-inputs/{id}")
    public SubmissionDtos.Input input(@PathVariable UUID id) { return service.input(id.toString()); }
}
