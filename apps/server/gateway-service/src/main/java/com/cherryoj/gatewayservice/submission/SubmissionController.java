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
    @GetMapping("/api/submissions")
    Mono<ResponseEntity<ApiSuccess<java.util.List<View>>>> history(@RequestParam UUID problemId,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String verdict,
            @RequestHeader("X-Expected-User-Id") UUID expectedUserId, ServerWebExchange exchange) {
        if (page < 1 || size < 1 || size > 100 || (verdict != null
                && !java.util.Set.of("AC","WA","PE","CE","RE","TLE","MLE","OLE","SE").contains(verdict))) {
            return Mono.error(new ApiProblemException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_HISTORY_QUERY","查询条件不合法","请检查提交记录查询条件。"));
        }
        String requestId=ApiRequestContext.requestId(exchange);
        return access.identity(exchange,requestId,expectedUserId)
                .flatMap(identity -> client.history(identity,problemId,page,size,verdict))
                .map(result -> ResponseEntity.ok().header("Cache-Control","no-store")
                        .body(ApiSuccess.of(result.items(),requestId,
                                new PagePagination(result.page(),result.size(),result.totalElements(),result.totalPages()))));
    }
    @GetMapping("/api/submissions/{id}/source")
    Mono<ResponseEntity<ApiSuccess<Source>>> source(@PathVariable UUID id,
            @RequestHeader("X-Expected-User-Id") UUID expectedUserId, ServerWebExchange exchange) {
        String requestId=ApiRequestContext.requestId(exchange);
        return access.identity(exchange,requestId,expectedUserId).flatMap(identity -> client.source(identity,id))
                .map(source -> ResponseEntity.ok().header("Cache-Control","no-store").body(ApiSuccess.of(source,requestId)));
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
    public record HistoryPage(java.util.List<View> items,int page,int size,long totalElements,int totalPages) {}
    public record Source(UUID submissionId,UUID problemId,UUID problemVersionId,String languageId,String source) {
        @Override public String toString() { return "Source[submissionId="+submissionId+", source=<redacted>]"; }
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
