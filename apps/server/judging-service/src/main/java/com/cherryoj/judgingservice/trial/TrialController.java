package com.cherryoj.judgingservice.trial;

import com.cherryoj.judgingservice.api.*;
import com.cherryoj.judgingservice.formal.FormalProperties;
import com.cherryoj.judgingservice.persistence.*;
import com.cherryoj.judgingservice.http.LimitedHttpBody;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Future;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
public class TrialController {

	private final SubmissionExecutionProfileController profiles;

	private final JudgeNodeRepository nodes;

	private final FormalProperties budgets;

	private final ObjectMapper json;

	private final HttpClient http;

	private final Semaphore slots = new Semaphore(1);

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public TrialController(SubmissionExecutionProfileController profiles, JudgeNodeRepository nodes,
			FormalProperties budgets, ObjectMapper json, HttpClient http) {
		this.profiles = profiles;
		this.nodes = nodes;
		this.budgets = budgets;
		this.json = json;
		this.http = http;
	}

	public record Request(@NotNull UUID problemId, @NotNull UUID problemVersionId, @NotNull UUID testDataVersionId,
			@NotBlank @Pattern(regexp = "[a-f0-9]{64}") String testDataContentSha256,
			@Pattern(regexp = "cpp") @NotNull String languageId, @NotBlank @Size(max = 262144) String source,
			@NotNull @Size(max = 65536) String inputText, @Positive long deadlineEpochMs) {
		@Override
		public String toString() {
			return "TrialRequest[private]";
		}
	}

	public record Output(String text, long capturedBytes, boolean truncated) {
		@Override
		public String toString() {
			return "Output[private]";
		}
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public record Result(String status, Long cpuNs, Long memoryBytes, Output stdout, Output stderr,
			String compileDiagnostic, SubmissionExecutionProfileController.Limits effectiveLimits) {
		@Override
		public String toString() {
			return "TrialResult[status=" + status + "]";
		}
	}

	@PostMapping("/internal/submission/trials")
	public ResponseEntity<Result> run(@Valid @RequestBody Request request,
			@RequestHeader(value = "traceparent", required = false) String trace) {
		if (request.source().getBytes(StandardCharsets.UTF_8).length > 262144
				|| request.inputText().getBytes(StandardCharsets.UTF_8).length > 65536)
			throw error(HttpStatus.PAYLOAD_TOO_LARGE, "RUN_INPUT_TOO_LARGE", "代码或输入过大。");
		long deadline = Math.min(request.deadlineEpochMs(), System.currentTimeMillis() + 45000);
		if (!slots.tryAcquire())
			throw error(HttpStatus.TOO_MANY_REQUESTS, "RUN_BUSY", "运行服务繁忙，请稍后重试。");
		// 0=未开始，1=工作线程持有槽位，2=启动前取消；取消与启动竞态只能释放一次。
		var started = new java.util.concurrent.atomic.AtomicInteger();
		var released = new java.util.concurrent.atomic.AtomicBoolean();
		Runnable release = () -> {
			if (released.compareAndSet(false, true))
				slots.release();
		};
		Future<Result> task;
		try {
			task = executor.submit(() -> {
				if (!started.compareAndSet(0, 1))
					throw unavailable();
				try {
					return execute(request, trace, deadline);
				}
				finally {
					release.run();
				}
			});
		}
		catch (RejectedExecutionException stopped) {
			slots.release();
			throw unavailable();
		}
		try {
			return ResponseEntity.ok()
				.header("Cache-Control", "no-store")
				.body(task.get(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS));
		}
		catch (InterruptedException e) {
			task.cancel(true);
			if (started.compareAndSet(0, 2))
				release.run();
			Thread.currentThread().interrupt();
			throw timeout();
		}
		catch (TimeoutException e) {
			task.cancel(true);
			if (started.compareAndSet(0, 2))
				release.run();
			throw timeout();
		}
		catch (ExecutionException e) {
			if (e.getCause() instanceof JudgingApiException known) {
				org.slf4j.LoggerFactory.getLogger(getClass())
					.warn("custom.run.failed stage=execution code={}", known.code());
				throw known;
			}
			org.slf4j.LoggerFactory.getLogger(getClass())
				.warn("custom.run.failed stage=execution code=UNEXPECTED_FAILURE");
			throw unavailable();
		}
	}

	private Result execute(Request r, String trace, long deadline) {
		var profile = profiles.resolve(new SubmissionExecutionProfileController.Request(r.problemVersionId().toString(),
				r.testDataVersionId().toString(), r.testDataContentSha256(), "cpp", 1, "trial"));
		var limits = profile.effectiveLimits();
		long clock = limits.clockNs() != null ? limits.clockNs()
				: Math.multiplyExact(limits.cpuNs(), budgets.wallRatio());
		var effective = new SubmissionExecutionProfileController.Limits(limits.cpuNs(), limits.memoryBytes(), clock);
		if (profile.executionBudgetNs() > TimeUnit.MILLISECONDS.toNanos(remaining(deadline)))
			throw error(HttpStatus.UNPROCESSABLE_ENTITY, "RUN_LIMIT_UNSUPPORTED", "此题目的执行预算超过自测期限。");
		var node = nodes.ready(profile.judgeEnvironmentId(), r.testDataVersionId().toString(),
				r.testDataContentSha256(), LocalDateTime.now(ZoneOffset.UTC));
		if (node == null || !node.fingerprint().equals(profile.environmentFingerprint()))
			throw unavailable();
		Map<String, Object> body = Map.of("submissionId", UUID.randomUUID().toString(), "problemId", r.problemId(),
				"problemVersionId", r.problemVersionId(), "testDataVersionId", r.testDataVersionId(), "languageId",
				"cpp", "source", r.source(), "limits", effective, "mode", "trial", "cases",
				List.of(Map.of("input", r.inputText())));
		var builder = HttpRequest.newBuilder(URI.create(node.endpoint().replaceAll("/$", "") + "/judge"))
			.timeout(Duration.ofMillis(remaining(deadline)))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
		if (trace != null && trace.matches("[a-f0-9]{2}-[a-f0-9]{32}-[a-f0-9]{16}-[a-f0-9]{2}"))
			builder.header("traceparent", trace);
		var response = http.sendAsync(builder.build(), ignored -> new LimitedHttpBody(2 * 1024 * 1024));
		try {
			var received = response.get(remaining(deadline), TimeUnit.MILLISECONDS);
			if (received.statusCode() != 200) {
				org.slf4j.LoggerFactory.getLogger(getClass())
					.warn("custom.run.failed stage=judge httpStatus={}", received.statusCode());
				throw unavailable();
			}
			var tree = json.readTree(received.body());
			if (!profile.environmentFingerprint().equals(tree.path("environmentFingerprint").asText()))
				throw unavailable();
			String verdict = tree.path("verdict").asText();
			if ("CE".equals(verdict))
				return new Result("COMPILE_ERROR", null, null, null, null, diagnostic(tree.path("message").asText("")),
						effective);
			String status = switch (verdict) {
				case "RAN" -> "COMPLETED";
				case "RE" -> "RUNTIME_ERROR";
				case "TLE" -> "TIME_LIMIT_EXCEEDED";
				case "MLE" -> "MEMORY_LIMIT_EXCEEDED";
				case "OLE" -> "OUTPUT_LIMIT_EXCEEDED";
				default -> throw unavailable();
			};
			var cases = tree.path("caseResults");
			if (!cases.isArray() || cases.size() != 1)
				throw unavailable();
			var c = cases.get(0);
			if (!verdict.equals(c.path("verdict").asText()) || c.path("idx").asInt() != 1)
				throw unavailable();
			// Missing streams indicate an old judge binary; never pretend stderr was
			// empty.
			return new Result(status, nonnegative(c, "cpuNs"), nonnegative(c, "memoryBytes"), output(c.path("output")),
					output(c.path("stderr")), null, effective);
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
			if (!response.isDone())
				response.cancel(true);
		}
	}

	private static long nonnegative(tools.jackson.databind.JsonNode n, String field) {
		var v = n.path(field);
		if (v.isMissingNode())
			return 0;
		if (!v.isIntegralNumber() || !v.canConvertToLong() || v.longValue() < 0)
			throw unavailable();
		return v.longValue();
	}

	private static Output output(tools.jackson.databind.JsonNode n) {
		if (!n.isObject() || !n.path("excerpt").isString() || !n.path("bytes").isIntegralNumber())
			throw unavailable();
		String text = n.path("excerpt").asText();
		long bytes = nonnegative(n, "bytes");
		if (bytes < text.getBytes(StandardCharsets.UTF_8).length)
			throw unavailable();
		return new Output(clip(text, 16384), bytes,
				n.path("truncated").asBoolean(false) || text.getBytes(StandardCharsets.UTF_8).length > 16384);
	}

	static String clip(String text, int max) {
		int end = 0, count = 0;
		while (end < text.length()) {
			int cp = text.codePointAt(end);
			int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
			if (count + size > max)
				break;
			count += size;
			end += Character.charCount(cp);
		}
		return text.substring(0, end);
	}

	static String diagnostic(String text) {
		return clip(text.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "")
			.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
			.replaceAll("(?:[A-Za-z]:)?[/\\\\](?:[^\\s:]+[/\\\\])*[^\\s:]+", "<file>"), 8192);
	}

	static long remaining(long deadline) {
		long ms = deadline - System.currentTimeMillis();
		if (ms <= 0)
			throw timeout();
		return ms;
	}

	static JudgingApiException error(HttpStatus status, String code, String detail) {
		return new JudgingApiException(status, code, detail);
	}

	static JudgingApiException unavailable() {
		return error(HttpStatus.SERVICE_UNAVAILABLE, "CUSTOM_RUN_UNAVAILABLE", "运行服务暂不可用。");
	}

	static JudgingApiException timeout() {
		return error(HttpStatus.GATEWAY_TIMEOUT, "CUSTOM_RUN_TIMEOUT", "本次运行结果未能确认，请稍后重试。");
	}

	@PreDestroy
	public void close() {
		executor.shutdownNow();
	}

}
