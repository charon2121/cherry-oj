package com.cherryoj.gatewayservice.trial;

import com.cherryoj.gatewayservice.api.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.cherryoj.gatewayservice.auth.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

@RestController
public class CustomRunController {

	private final SubmissionGatewayAccess access;

	private final InternalRequestFactory requests;

	private final WebClient client;

	private final ReactiveStringRedisTemplate redis;

	private final boolean enabled;

	// 只保护实际在途请求；TTL 是进程崩溃兜底，不是两次运行之间的冷却。
	private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
			if redis.call('SET',KEYS[1],ARGV[1],'NX','PX',65000) then return 0 end
			return 1
			""", Long.class);

	private static final RedisScript<Long> RELEASE = RedisScript
		.of("if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end return 0", Long.class);

	public CustomRunController(SubmissionGatewayAccess access, InternalRequestFactory requests,
			WebClient.Builder builder, ReactiveStringRedisTemplate redis,
			@Value("${cherry.gateway.submission-service-url:http://127.0.0.1:8083}") String url,
			@Value("${cherry.custom-run.enabled:false}") boolean enabled) {
		this.access = access;
		this.requests = requests;
		this.redis = redis;
		this.enabled = enabled;
		client = builder.clone().baseUrl(url).codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024)).build();
	}

	public record Request(@NotNull UUID problemId, @NotNull UUID expectedProblemVersionId,
			@NotNull @Pattern(regexp = "cpp") String languageId, @NotBlank @Size(max = 262144) String source,
			@NotNull @Size(max = 65536) String inputText) {
		@Override
		public String toString() {
			return "CustomRunRequest[private]";
		}
	}

	public record Output(String text, @JsonProperty(required = true) long capturedBytes,
			@JsonProperty(required = true) boolean truncated) {
		@Override
		public String toString() {
			return "RunOutput[private]";
		}
	}

	public record Limits(@JsonProperty(required = true) long cpuNs, @JsonProperty(required = true) long memoryBytes,
			@JsonProperty(required = true) long clockNs) {
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public record Result(UUID problemId, UUID problemVersionId, int problemVersionNo, String languageId, String status,
			Long cpuNs, Long memoryBytes, Output stdout, Output stderr, String compileDiagnostic,
			Limits effectiveLimits) {
		@Override
		public String toString() {
			return "CustomRunResult[status=" + status + "]";
		}
	}

	@PostMapping("/api/custom-runs")
	public Mono<ResponseEntity<ApiSuccess<Result>>> run(@Valid @RequestBody Request body,
			@RequestHeader("X-Expected-User-Id") UUID expected, ServerWebExchange exchange) {
		if (!enabled)
			return Mono.error(error(HttpStatus.SERVICE_UNAVAILABLE, "CUSTOM_RUN_DISABLED", "自定义运行暂未开放。"));
		if (body.source().getBytes(StandardCharsets.UTF_8).length > 262144
				|| body.inputText().getBytes(StandardCharsets.UTF_8).length > 65536)
			return Mono.error(error(HttpStatus.PAYLOAD_TOO_LARGE, "RUN_INPUT_TOO_LARGE", "源码或输入超过大小限制。"));
		String id = ApiRequestContext.requestId(exchange), owner = UUID.randomUUID().toString();
		List<String> keys = List.of("cherry:custom-run:{" + expected + "}:active");
		return access.identity(exchange, id, expected).flatMap(identity -> withLease(keys, owner, () -> {
			return requests
				.authenticated(
						client.post()
							.uri("/api/custom-runs")
							.header("X-Custom-Run-Deadline", Long.toString(System.currentTimeMillis() + 45000)),
						identity)
				.bodyValue(body)
				.exchangeToMono(response -> {
					if (response.statusCode().value() == 200)
						return response.bodyToMono(Result.class)
							.map(value -> validate(value, body))
							.map(value -> ResponseEntity.ok()
								.header("Cache-Control", "no-store")
								.body(ApiSuccess.of(value, id)));
					int status = response.statusCode().value();
					return response.releaseBody().then(Mono.defer(() -> {
						ApiProblemException failure = switch (status) {
							case 404 -> error(HttpStatus.NOT_FOUND, "PROBLEM_NOT_AVAILABLE", "题目不存在或不再公开。");
							case 409 -> error(HttpStatus.CONFLICT, "PROBLEM_VERSION_CHANGED", "题目版本已更新，请打开新版本后再运行。");
							case 413 -> error(HttpStatus.PAYLOAD_TOO_LARGE, "RUN_INPUT_TOO_LARGE", "源码或输入过大。");
							case 422 ->
								error(HttpStatus.UNPROCESSABLE_ENTITY, "RUN_LIMIT_UNSUPPORTED", "此题目的执行配置暂不支持自测。");
							case 429 -> error(HttpStatus.TOO_MANY_REQUESTS, "RUN_BUSY", "运行服务繁忙，请稍后重试。");
							case 504 -> timeout();
							default -> unavailable();
						};
						if (status == 429)
							exchange.getResponse().getHeaders().set("Retry-After", "2");
						org.slf4j.LoggerFactory.getLogger(getClass())
							.warn("custom.run.failed stage=submission httpStatus={} code={}", status, failure.code());
						return Mono.error(failure);
					}));
				})
				.switchIfEmpty(Mono.error(unavailable()))
				.timeout(Duration.ofSeconds(55))
				.onErrorMap(e -> e instanceof ApiProblemException ? e
						: e instanceof java.util.concurrent.TimeoutException ? timeout() : unavailable());
		}));
	}

	// cache 保证取消不打断 Redis 准入结果；清理等待准入结束，再按 owner 删除。
	// usingWhen 在响应交付前完成清理，也覆盖异常和断连；繁忙请求不能释放别人的租约。
	<T> Mono<T> withLease(List<String> keys, String owner, java.util.function.Supplier<Mono<T>> action) {
		return Mono.defer(() -> {
			Mono<Long> acquired = redis.execute(ACQUIRE, keys, List.of(owner)).single().cache();
			java.util.function.Function<String, Mono<Void>> cleanup = ignored -> acquired
				.flatMap(result -> result == 0 ? release(keys, owner) : Mono.<Void>empty())
				.then()
				.timeout(Duration.ofSeconds(2))
				.onErrorResume(error -> Mono.empty());
			return Mono.usingWhen(Mono.just(owner),
					ignored -> acquired.timeout(Duration.ofSeconds(2))
						.onErrorMap(error -> unavailable())
						.flatMap(result -> result == 0 ? Mono.defer(action)
								: Mono.error(error(HttpStatus.TOO_MANY_REQUESTS, "RUN_BUSY", "已有运行正在进行，请等待本次运行结束。"))),
					cleanup, (ignored, error) -> cleanup.apply(ignored), cleanup);
		});
	}

	// Redis 故障时保留 TTL 兜底，不掩盖原始运行结果。
	private Mono<Void> release(List<String> keys, String owner) {
		return redis.execute(RELEASE, List.of(keys.get(0)), List.of(owner))
			.then()
			.timeout(Duration.ofSeconds(2))
			.onErrorResume(e -> Mono.empty());
	}

	static Result validate(Result v, Request r) {
		if (v == null || !r.problemId().equals(v.problemId())
				|| !r.expectedProblemVersionId().equals(v.problemVersionId()) || v.problemVersionNo() < 1
				|| !"cpp".equals(v.languageId()) || v.status() == null
				|| !Set
					.of("COMPLETED", "COMPILE_ERROR", "RUNTIME_ERROR", "TIME_LIMIT_EXCEEDED", "MEMORY_LIMIT_EXCEEDED",
							"OUTPUT_LIMIT_EXCEEDED")
					.contains(v.status())
				|| v.effectiveLimits() == null)
			throw unavailable();
		var l = v.effectiveLimits();
		if (l.cpuNs() < 0 || l.memoryBytes() < 0 || l.clockNs() < 0)
			throw unavailable();
		if ("COMPILE_ERROR".equals(v.status())) {
			if (v.stdout() != null || v.stderr() != null || v.cpuNs() != null || v.memoryBytes() != null
					|| v.compileDiagnostic() == null
					|| v.compileDiagnostic().getBytes(StandardCharsets.UTF_8).length > 8192)
				throw unavailable();
		}
		else {
			checkOutput(v.stdout());
			checkOutput(v.stderr());
			if (v.cpuNs() == null || v.cpuNs() < 0 || v.memoryBytes() == null || v.memoryBytes() < 0
					|| v.compileDiagnostic() != null)
				throw unavailable();
		}
		return v;
	}

	private static void checkOutput(Output o) {
		if (o == null || o.text() == null || o.text().getBytes(StandardCharsets.UTF_8).length > 16384
				|| o.capturedBytes() < o.text().getBytes(StandardCharsets.UTF_8).length)
			throw unavailable();
	}

	static ApiProblemException error(HttpStatus status, String code, String detail) {
		return new ApiProblemException(status, code, "自定义运行未完成", detail);
	}

	static ApiProblemException unavailable() {
		return error(HttpStatus.SERVICE_UNAVAILABLE, "CUSTOM_RUN_UNAVAILABLE", "运行服务暂不可用，请稍后重试。");
	}

	static ApiProblemException timeout() {
		return error(HttpStatus.GATEWAY_TIMEOUT, "CUSTOM_RUN_TIMEOUT", "本次运行结果未能确认，请稍后重试。");
	}

}
