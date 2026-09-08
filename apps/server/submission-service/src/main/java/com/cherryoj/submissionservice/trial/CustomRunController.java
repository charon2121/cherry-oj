package com.cherryoj.submissionservice.trial;

import com.cherryoj.submissionservice.integration.SubmissionPrerequisites;
import com.cherryoj.submissionservice.api.SubmissionException;
import com.cherryoj.submissionservice.security.CurrentIdentity;
import com.cherryoj.identitysecurity.service.ServiceCredentials;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
public class CustomRunController {

	private final SubmissionPrerequisites prerequisites;

	private final ObjectMapper json;

	private final String url, token;

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(2))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	public CustomRunController(SubmissionPrerequisites prerequisites, ObjectMapper json,
			@Value("${cherry.submission.judging-url}") String url,
			@Value("${cherry.submission.judging-token:}") String token) {
		this.prerequisites = prerequisites;
		this.json = json;
		this.url = url;
		this.token = token;
	}

	public record Request(@NotNull UUID problemId, @NotNull UUID expectedProblemVersionId,
			@NotNull @Pattern(regexp = "cpp") String languageId, @NotBlank @Size(max = 262144) String source,
			@NotNull @Size(max = 65536) String inputText) {
		@Override
		public String toString() {
			return "CustomRunRequest[private]";
		}
	}

	@PostMapping("/api/custom-runs")
	public ResponseEntity<tools.jackson.databind.JsonNode> run(@Valid @RequestBody Request r,
			JwtAuthenticationToken identity,
			@RequestHeader(value = "X-Custom-Run-Deadline", required = false) Long outerDeadline,
			@RequestHeader(value = "traceparent", required = false) String trace) {
		CurrentIdentity.from(identity);
		long deadline = Math.min(outerDeadline == null ? Long.MAX_VALUE : outerDeadline,
				System.currentTimeMillis() + 45000);
		if (r.source().getBytes(StandardCharsets.UTF_8).length > 262144
				|| r.inputText().getBytes(StandardCharsets.UTF_8).length > 65536)
			throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "RUN_INPUT_TOO_LARGE", "源码或输入超过大小限制。");
		var s = prerequisites.snapshot(r.problemId().toString(), r.languageId());
		if (s == null || !r.problemId().toString().equals(s.problemId()) || !"ACM".equals(s.codeMode())
				|| !"cpp".equals(s.languageId()) || s.problemVersionId() == null || s.testDataVersionId() == null
				|| s.problemVersionNo() < 1 || s.testDataContentSha256() == null
				|| !s.testDataContentSha256().matches("[a-f0-9]{64}"))
			throw unavailable();
		if (!r.expectedProblemVersionId().toString().equals(s.problemVersionId()))
			throw failure(HttpStatus.CONFLICT, "PROBLEM_VERSION_CHANGED", "题目版本已更新，请打开新版本后再运行。");
		try {
			UUID.fromString(s.problemVersionId());
			UUID.fromString(s.testDataVersionId());
			ServiceCredentials.validate(token);
		}
		catch (RuntimeException invalid) {
			throw unavailable();
		}
		var body = Map.of("problemId", s.problemId(), "problemVersionId", s.problemVersionId(), "testDataVersionId",
				s.testDataVersionId(), "testDataContentSha256", s.testDataContentSha256(), "languageId", "cpp",
				"source", r.source(), "inputText", r.inputText(), "deadlineEpochMs", deadline);
		long remaining = deadline - System.currentTimeMillis();
		if (remaining <= 0)
			throw timeout();
		var builder = HttpRequest.newBuilder(URI.create(url.replaceAll("/$", "") + "/internal/submission/trials"))
			.timeout(Duration.ofMillis(remaining))
			.header("Content-Type", "application/json")
			.header("Authorization", "Bearer " + token)
			.POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
		if (trace != null && trace.matches("[a-f0-9]{2}-[a-f0-9]{32}-[a-f0-9]{16}-[a-f0-9]{2}"))
			builder.header("traceparent", trace);
		var future = http.sendAsync(builder.build(), ignored -> new LimitedBody(1024 * 1024));
		try {
			var response = future.get(remaining, TimeUnit.MILLISECONDS);
			if (response.statusCode() != 200) {
				org.slf4j.LoggerFactory.getLogger(getClass())
					.warn("custom.run.failed stage=judging httpStatus={}", response.statusCode());
				throw switch (response.statusCode()) {
					case 429 -> failure(HttpStatus.TOO_MANY_REQUESTS, "RUN_BUSY", "运行服务繁忙，请稍后重试。");
					case 422 -> failure(HttpStatus.UNPROCESSABLE_ENTITY, "RUN_LIMIT_UNSUPPORTED", "此题目的执行预算超过自测期限。");
					case 504 -> timeout();
					default -> unavailable();
				};
			}
			var result = json.readTree(response.body());
			if (!result.isObject() || !Set
				.of("COMPLETED", "COMPILE_ERROR", "RUNTIME_ERROR", "TIME_LIMIT_EXCEEDED", "MEMORY_LIMIT_EXCEEDED",
						"OUTPUT_LIMIT_EXCEEDED")
				.contains(result.path("status").asText()))
				throw unavailable();
			var output = json.createObjectNode();
			for (String field : List.of("status", "cpuNs", "memoryBytes", "stdout", "stderr", "compileDiagnostic",
					"effectiveLimits"))
				if (result.has(field))
					output.set(field, result.get(field));
			output.put("problemId", s.problemId());
			output.put("problemVersionId", s.problemVersionId());
			output.put("problemVersionNo", s.problemVersionNo());
			output.put("languageId", "cpp");
			return ResponseEntity.ok().header("Cache-Control", "no-store").body(output);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw timeout();
		}
		catch (TimeoutException e) {
			throw timeout();
		}
		catch (ExecutionException e) {
			if (e.getCause() instanceof HttpTimeoutException)
				throw timeout();
			throw unavailable();
		}
		finally {
			if (!future.isDone())
				future.cancel(true);
		}
	}

	private static SubmissionException failure(HttpStatus s, String c, String m) {
		return new SubmissionException(s, c, m);
	}

	private static SubmissionException unavailable() {
		return failure(HttpStatus.SERVICE_UNAVAILABLE, "CUSTOM_RUN_UNAVAILABLE", "运行服务暂不可用。");
	}

	private static SubmissionException timeout() {
		return failure(HttpStatus.GATEWAY_TIMEOUT, "CUSTOM_RUN_TIMEOUT", "本次运行结果未能确认，请稍后重试。");
	}

}
