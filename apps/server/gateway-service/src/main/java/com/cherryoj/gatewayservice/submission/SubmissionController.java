package com.cherryoj.gatewayservice.submission;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.cherryoj.gatewayservice.api.*;
import com.cherryoj.gatewayservice.auth.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public final class SubmissionController {
    private final SubmissionGatewayAccess access;
    private final SubmissionServiceClient client;
    SubmissionController(SubmissionGatewayAccess access,SubmissionServiceClient client) { this.access=access; this.client=client; }

    @PostMapping("/api/submissions")
    Mono<ResponseEntity<ApiSuccess<View>>> create(@RequestHeader("Idempotency-Key") UUID key,
            @RequestHeader("X-Expected-User-Id") UUID expectedUserId,
            @Valid @RequestBody Create body,ServerWebExchange exchange) {
        String requestId=ApiRequestContext.requestId(exchange);
        return access.identity(exchange,requestId,expectedUserId).flatMap(identity -> client.create(identity,key,body))
                .map(result -> ResponseEntity.status(result.getStatusCode()).header("Cache-Control","no-store")
                        .location(URI.create("/api/submissions/"+result.getBody().id()))
                        .body(ApiSuccess.of(result.getBody(),requestId)));
    }
    @GetMapping("/api/submissions/{id}")
    Mono<ResponseEntity<ApiSuccess<View>>> get(@PathVariable UUID id,ServerWebExchange exchange) {
        return read("/api/submissions/"+id,exchange);
    }
    @GetMapping("/api/submission-requests/{key}")
    Mono<ResponseEntity<ApiSuccess<View>>> request(@PathVariable UUID key,ServerWebExchange exchange) {
        return read("/api/submission-requests/"+key,exchange);
    }
    private Mono<ResponseEntity<ApiSuccess<View>>> read(String path,ServerWebExchange exchange) {
        String requestId=ApiRequestContext.requestId(exchange);
        return access.identity(exchange,requestId).flatMap(identity -> client.read(identity,path))
                .map(view -> ResponseEntity.ok().header("Cache-Control","no-store").body(ApiSuccess.of(view,requestId)));
    }
    public record Create(@NotNull UUID problemId,@NotNull UUID expectedProblemVersionId,
            @NotBlank @Pattern(regexp="cpp") String languageId,@NotBlank @Size(max=262144) String source) {
        @Override public String toString() { return "Create[source=<redacted>]"; }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record View(UUID id,UUID problemId,UUID problemVersionId,int problemVersionNo,
            String problemTitle,String languageId,String status,Instant createdAt,String verdict,
            Long cpuNs,Long memoryBytes,Integer passedCount,Integer executedCount,Integer totalCount,
            String message,Instant finishedAt) {}
}
