package com.cherryoj.gatewayservice.problem;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.api.ApiSuccess;
import com.cherryoj.gatewayservice.auth.AdminGatewayAccess;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

@RestController
@Validated
@RequestMapping("/api/admin/problems/{problemId}/authoring/jobs")
public class AdminAuthoringController {
    private static final String UUID = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    private static final int MAX_BYTES = 8 << 20;
    private final AdminGatewayAccess access;
    private final ProblemServiceClient problems;

    AdminAuthoringController(AdminGatewayAccess access, ProblemServiceClient problems) {
        this.access = access;
        this.problems = problems;
    }

    @GetMapping
    Mono<ResponseEntity<ApiSuccess<JsonNode>>> list(
            @PathVariable @Pattern(regexp = UUID) String problemId, ServerWebExchange exchange) {
        return call(exchange, HttpMethod.GET, problemId, null, null, HttpStatus.OK, false);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<ApiSuccess<JsonNode>>> create(
            @PathVariable @Pattern(regexp = UUID) String problemId, ServerWebExchange exchange) {
        return call(exchange, HttpMethod.POST, problemId, null, null, HttpStatus.ACCEPTED, true);
    }

    @GetMapping("/{jobId}")
    Mono<ResponseEntity<ApiSuccess<JsonNode>>> get(
            @PathVariable @Pattern(regexp = UUID) String problemId,
            @PathVariable @Pattern(regexp = UUID) String jobId,
            ServerWebExchange exchange) {
        return call(exchange, HttpMethod.GET, problemId, jobId, null, HttpStatus.OK, false);
    }

    @GetMapping(value = "/{jobId}/report", produces = "text/plain;charset=UTF-8")
    Mono<ResponseEntity<String>> report(
            @PathVariable @Pattern(regexp = UUID) String problemId,
            @PathVariable @Pattern(regexp = UUID) String jobId,
            ServerWebExchange exchange) {
        String requestId = ApiRequestContext.requestId(exchange);
        return access.actingUser(exchange, requestId)
                .flatMap(identity -> problems.authoringReport(identity, problemId, jobId))
                .onErrorMap(error -> ProblemApiErrors.map(error, false, requestId))
                .map(report -> ResponseEntity.ok()
                        .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                        .cacheControl(CacheControl.noStore())
                        .body(report));
    }

    @PostMapping("/{jobId}/retry")
    Mono<ResponseEntity<ApiSuccess<JsonNode>>> retry(
            @PathVariable @Pattern(regexp = UUID) String problemId,
            @PathVariable @Pattern(regexp = UUID) String jobId,
            ServerWebExchange exchange) {
        return call(exchange, HttpMethod.POST, problemId, jobId, "retry", HttpStatus.ACCEPTED, false);
    }

    @PostMapping("/{jobId}/cancel")
    Mono<ResponseEntity<ApiSuccess<JsonNode>>> cancel(
            @PathVariable @Pattern(regexp = UUID) String problemId,
            @PathVariable @Pattern(regexp = UUID) String jobId,
            ServerWebExchange exchange) {
        return call(exchange, HttpMethod.POST, problemId, jobId, "cancel", HttpStatus.OK, false);
    }

    @PostMapping(value = "/{jobId}/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<ApiSuccess<JsonNode>>> apply(
            @PathVariable @Pattern(regexp = UUID) String problemId,
            @PathVariable @Pattern(regexp = UUID) String jobId,
            ServerWebExchange exchange) {
        return call(exchange, HttpMethod.POST, problemId, jobId, "apply", HttpStatus.OK, true);
    }

    private Mono<ResponseEntity<ApiSuccess<JsonNode>>> call(
            ServerWebExchange exchange,
            HttpMethod method,
            String problemId,
            String jobId,
            String action,
            HttpStatus success,
            boolean body) {
        String requestId = ApiRequestContext.requestId(exchange);
        return access.actingUser(exchange, requestId)
                .flatMap(identity -> (body ? read(exchange) : Mono.just(new byte[0]))
                        .flatMap(bytes -> problems.authoring(identity, method, problemId, jobId, action, bytes)))
                .onErrorMap(error -> ProblemApiErrors.map(error, false, requestId))
                .map(value -> {
                    var response = ResponseEntity.status(success).cacheControl(CacheControl.noStore());
                    if (success == HttpStatus.ACCEPTED) {
                        String id = value.path("job").path("id").asText();
                        if (!id.matches(UUID))
                            throw new ApiProblemException(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", "上游响应无效", "出题任务标识无效。");
                        response.location(URI.create("/api/admin/problems/" + problemId + "/authoring/jobs/" + id));
                    }
                    return response.body(ApiSuccess.of(value, requestId));
                });
    }

    private static Mono<byte[]> read(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody(), MAX_BYTES)
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .switchIfEmpty(Mono.error(new ApiProblemException(
                        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求格式错误", "出题请求需要一个 JSON 对象。")))
                .onErrorMap(
                        DataBufferLimitException.class,
                        error -> new ApiProblemException(
                                HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "请求体过大", "出题输入超过 8 MiB。"));
    }
}
