package com.cherryoj.gatewayservice.problem;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePartEvent;
import org.springframework.http.codec.multipart.PartEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.api.ApiSuccess;
import com.cherryoj.gatewayservice.api.PagePagination;
import com.cherryoj.gatewayservice.auth.AdminGatewayAccess;
import com.cherryoj.gatewayservice.auth.DelegatedIdentity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/problems")
@Validated
public class AdminProblemsController {

	private static final String UUID =
			"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
	private static final MediaType APPLICATION_ZIP = MediaType.parseMediaType("application/zip");

	private final AdminGatewayAccess adminAccess;
	private final ProblemServiceClient problemService;

	AdminProblemsController(AdminGatewayAccess adminAccess, ProblemServiceClient problemService) {
		this.adminAccess = adminAccess;
		this.problemService = problemService;
	}

	@GetMapping
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblemListData>>> list(
			@RequestParam(required = false) @Size(min = 1, max = 100) String q,
			@RequestParam(required = false) @Pattern(regexp = "^(ACTIVE|ARCHIVED)$") String status,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId, identity -> problemService
				.listAdmin(identity, q, status, page, size))
				.map(result -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
						.body(ApiSuccess.of(new ProblemDtos.AdminProblemListData(result.items()), requestId,
								new PagePagination(result.page(), result.size(),
										result.totalElements(), result.totalPages()))));
	}

	@PostMapping
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblem>>> create(
			@Valid @RequestBody CreateProblemRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId, identity -> problemService.createProblem(identity, request))
				.map(problem -> ResponseEntity.created(URI.create("/api/admin/problems/" + problem.id()))
						.cacheControl(CacheControl.noStore()).body(ApiSuccess.of(problem, requestId)));
	}

	@GetMapping("/{problemId}")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblem>>> get(
			@PathVariable @Pattern(regexp = UUID) String problemId, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				identity -> problemService.getProblem(identity, problemId))
				.map(problem -> ok(problem, requestId));
	}

	@PatchMapping("/{problemId}")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblem>>> update(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@Valid @RequestBody UpdateProblemRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				identity -> problemService.updateProblem(identity, problemId, request))
				.map(problem -> ok(problem, requestId));
	}

	@PostMapping("/{problemId}/archive")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblem>>> archive(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@Valid @RequestBody RowVersionRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				identity -> problemService.archiveProblem(identity, problemId, request))
				.map(problem -> ok(problem, requestId));
	}

	@DeleteMapping("/{problemId}")
	Mono<ResponseEntity<Void>> delete(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@RequestParam @Min(0) long rowVersion, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				identity -> problemService.deleteProblem(identity, problemId, rowVersion))
				.thenReturn(ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
	}

	@GetMapping("/{problemId}/preview")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.ProblemDetail>>> preview(
			@PathVariable @Pattern(regexp = UUID) String problemId, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId, identity -> problemService.preview(identity, problemId))
				.map(detail -> ok(detail, requestId));
	}

	@GetMapping("/{problemId}/test-data")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.TestData>>> getTestData(
			@PathVariable @Pattern(regexp = UUID) String problemId, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId, identity -> problemService.getTestData(identity, problemId))
				.map(testData -> ok(testData, requestId));
	}

	@PutMapping(path = "/{problemId}/test-data", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.TestData>>> replaceTestData(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@RequestBody Flux<PartEvent> parts, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				identity -> problemService.replaceTestData(identity, problemId, zipContent(parts)))
				.map(testData -> ok(testData, requestId));
	}

	@PostMapping("/{problemId}/calibration")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.LanguageCalibration>>> calibrate(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@Valid @RequestBody CalibrateProblemRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				identity -> problemService.calibrate(identity, problemId, request))
				.map(calibration -> ok(calibration, requestId));
	}

	@GetMapping("/{problemId}/publish-check")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.PublishCheck>>> publishCheck(
			@PathVariable @Pattern(regexp = UUID) String problemId, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId, identity -> problemService.publishCheck(identity, problemId))
				.map(check -> ok(check, requestId));
	}

	@PostMapping("/{problemId}/publish")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblem>>> publish(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@Valid @RequestBody RowVersionRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				identity -> problemService.publish(identity, problemId, request))
				.map(problem -> ok(problem, requestId));
	}

	@PostMapping("/{problemId}/unpublish")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblem>>> unpublish(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@Valid @RequestBody RowVersionRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				identity -> problemService.unpublish(identity, problemId, request))
				.map(problem -> ok(problem, requestId));
	}

	private <T> Mono<T> admin(
			ServerWebExchange exchange, String requestId, Function<DelegatedIdentity, Mono<T>> action) {
		return adminAccess.delegatedIdentity(exchange, requestId).flatMap(action)
				.onErrorMap(error -> ProblemApiErrors.map(error, false, requestId));
	}

	private static String requestId(ServerWebExchange exchange) {
		return ApiRequestContext.requestId(exchange);
	}

	private static Flux<DataBuffer> zipContent(Flux<PartEvent> parts) {
		return Flux.defer(() -> {
			AtomicBoolean completed = new AtomicBoolean();
			return parts.switchOnFirst((signal, stream) -> {
				if (!signal.hasValue() || !(signal.get() instanceof FilePartEvent first)
						|| !"file".equals(first.name())) {
					if (signal.hasValue()) {
						DataBufferUtils.release(signal.get().content());
					}
					return Flux.error(invalidMultipart());
				}
				return stream.<DataBuffer>handle((part, sink) -> {
					if (completed.get() || !(part instanceof FilePartEvent)
							|| !"file".equals(part.name())) {
						DataBufferUtils.release(part.content());
						sink.error(invalidMultipart());
						return;
					}
					sink.next(part.content());
					if (part.isLast()) {
						completed.set(true);
					}
				}).concatWith(Flux.defer(() -> completed.get()
						? Flux.empty()
						: Flux.error(invalidMultipart())));
			});
		});
	}

	private static ApiProblemException invalidMultipart() {
		return new ApiProblemException(
				HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST", "请求格式错误", "multipart 必须且只能包含一个 file 文件部分。");
	}

	private static <T> ResponseEntity<ApiSuccess<T>> ok(T body, String requestId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(ApiSuccess.of(body, requestId));
	}

	record CreateProblemRequest(
			@NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 128) String slug,
			@NotBlank @Size(max = 512) String title,
			@NotBlank @Pattern(regexp = "^(UNRATED|EASY|MEDIUM|HARD)$") String difficulty,
			@NotBlank @Pattern(regexp = "^ACM$") String codeMode,
			@NotBlank @Pattern(regexp = "^cpp$") String languageId) {
	}

	/** 题目没有版本：一次请求保存题目的全部可编辑内容。 */
	record UpdateProblemRequest(
			@NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 128) String slug,
			@NotBlank @Size(max = 512) String title,
			@NotNull @Size(max = 16_777_215) String statementMarkdown,
			@NotNull @Size(max = 16_777_215) String inputDescriptionMarkdown,
			@NotNull @Size(max = 16_777_215) String outputDescriptionMarkdown,
			@Size(max = 16_777_215) String constraintsMarkdown,
			@Size(max = 16_777_215) String hintMarkdown,
			@NotBlank @Pattern(regexp = "^(UNRATED|EASY|MEDIUM|HARD)$") String difficulty,
			@NotNull @Size(max = 20) List<@NotBlank @Size(max = 32) String> tags,
			@NotNull @Size(max = 100) List<@Valid SampleInput> samples,
			@NotNull @Size(max = 1_048_576) String starterCode,
			@Min(0) long rowVersion) {
	}

	record RowVersionRequest(@Min(0) long rowVersion) {
	}

	record SampleInput(
			@Min(1) @Max(100) int ordinal,
			@NotNull @Size(max = 1_048_576) String input,
			@NotNull @Size(max = 1_048_576) String output,
			@Size(max = 1_048_576) String explanationMarkdown) {
	}

	record CalibrateProblemRequest(
			@NotBlank @Pattern(regexp = "^cpp$") String languageId,
			@Min(1) long cpuNs,
			@Min(1) long memoryBytes,
			@Min(1) Long clockNs,
			@NotBlank @Size(max = 1_048_576) String referenceSource) {
	}
}
