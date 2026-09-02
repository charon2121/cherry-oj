package com.cherryoj.gatewayservice.problem;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.api.ApiSuccess;
import com.cherryoj.gatewayservice.api.PagePagination;
import com.cherryoj.gatewayservice.auth.AdminGatewayAccess;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/problems")
@Validated
public class AdminProblemsController {

	private static final String UUID =
			"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
	private static final String SHA256 = "^[a-f0-9]{64}$";
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
		return adminRead(exchange, requestId, token -> problemService
				.listAdmin(token, q, status, page, size, requestId))
				.map(result -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
						.body(ApiSuccess.of(new ProblemDtos.AdminProblemListData(result.items()), requestId,
								new PagePagination(result.page(), result.size(),
										result.totalElements(), result.totalPages()))));
	}

	@PostMapping
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblem>>> create(
			@Valid @RequestBody CreateProblemRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId, token -> problemService.createProblem(token, request, requestId))
				.map(problem -> ResponseEntity.created(URI.create("/api/admin/problems/" + problem.id()))
						.cacheControl(CacheControl.noStore()).body(ApiSuccess.of(problem, requestId)));
	}

	@GetMapping("/{problemId}")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblem>>> get(
			@PathVariable @Pattern(regexp = UUID) String problemId, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return adminRead(exchange, requestId,
				token -> problemService.getProblem(token, problemId, requestId))
				.map(problem -> ok(problem, requestId));
	}

	@PatchMapping("/{problemId}")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblem>>> update(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@Valid @RequestBody UpdateProblemRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				token -> problemService.updateProblem(token, problemId, request, requestId))
				.map(problem -> ok(problem, requestId));
	}

	@PostMapping("/{problemId}/archive")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblem>>> archive(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@Valid @RequestBody RowVersionRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				token -> problemService.archiveProblem(token, problemId, request, requestId))
				.map(problem -> ok(problem, requestId));
	}

	@PostMapping("/{problemId}/versions")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblemVersion>>> createRevision(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@Valid @RequestBody CreateRevisionRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				token -> problemService.createRevision(token, problemId, request, requestId))
				.map(version -> ResponseEntity.created(URI.create(
						"/api/admin/problems/" + problemId + "/versions/" + version.id()))
						.cacheControl(CacheControl.noStore()).body(ApiSuccess.of(version, requestId)));
	}

	@GetMapping("/{problemId}/versions/{problemVersionId}")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblemVersion>>> getVersion(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@PathVariable @Pattern(regexp = UUID) String problemVersionId,
			ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return adminRead(exchange, requestId, token -> problemService
				.getVersion(token, problemId, problemVersionId, requestId))
				.map(version -> ok(version, requestId));
	}

	@PatchMapping("/{problemId}/versions/{problemVersionId}")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblemVersion>>> updateVersion(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@PathVariable @Pattern(regexp = UUID) String problemVersionId,
			@Valid @RequestBody UpdateVersionRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId, token -> problemService
				.updateVersion(token, problemId, problemVersionId, request, requestId))
				.map(version -> ok(version, requestId));
	}

	@DeleteMapping("/{problemId}/versions/{problemVersionId}")
	Mono<ResponseEntity<Void>> deleteVersion(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@PathVariable @Pattern(regexp = UUID) String problemVersionId,
			@RequestParam @Min(0) long rowVersion, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId, token -> problemService
				.deleteVersion(token, problemId, problemVersionId, rowVersion, requestId))
				.thenReturn(ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
	}

	@GetMapping("/{problemId}/versions/{problemVersionId}/preview")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.ProblemDetail>>> preview(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@PathVariable @Pattern(regexp = UUID) String problemVersionId,
			ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return adminRead(exchange, requestId,
				token -> problemService.preview(token, problemId, problemVersionId, requestId))
				.map(detail -> ok(detail, requestId));
	}

	@GetMapping("/{problemId}/test-data")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.TestDataVersionListData>>> listTestData(
			@PathVariable @Pattern(regexp = UUID) String problemId, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return adminRead(exchange, requestId,
				token -> problemService.listTestData(token, problemId, requestId))
				.map(items -> ok(new ProblemDtos.TestDataVersionListData(items), requestId));
	}

	@PostMapping(path = "/{problemId}/test-data", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.TestDataVersion>>> uploadTestData(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@RequestPart("file") FilePart file, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				token -> problemService.uploadTestData(token, problemId, file, requestId))
				.map(testData -> ResponseEntity.created(URI.create(
						"/api/admin/problems/" + problemId + "/test-data/" + testData.id()))
						.cacheControl(CacheControl.noStore()).body(ApiSuccess.of(testData, requestId)));
	}

	@GetMapping("/{problemId}/test-data/{testDataVersionId}/download")
	Mono<ResponseEntity<Flux<DataBuffer>>> downloadTestData(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@PathVariable @Pattern(regexp = UUID) String testDataVersionId,
			ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId, token -> problemService
				.downloadTestData(token, problemId, testDataVersionId, requestId))
				.map(download -> {
					ResponseEntity.BodyBuilder response = ResponseEntity.ok()
							.contentType(APPLICATION_ZIP).cacheControl(CacheControl.noStore())
							.header(HttpHeaders.CONTENT_DISPOSITION,
									"attachment; filename=\"" + download.filename() + "\"");
					if (download.contentLength() != null) {
						response.contentLength(download.contentLength());
					}
					return response.body(download.body());
				});
	}

	@PutMapping("/{problemId}/versions/{problemVersionId}/test-data")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblemVersion>>> bindTestData(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@PathVariable @Pattern(regexp = UUID) String problemVersionId,
			@Valid @RequestBody BindTestDataRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId, token -> problemService
				.bindTestData(token, problemId, problemVersionId, request, requestId))
				.map(version -> ok(version, requestId));
	}

	@PostMapping("/{problemId}/versions/{problemVersionId}/deployment")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.TestDataDeployment>>> deploy(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@PathVariable @Pattern(regexp = UUID) String problemVersionId,
			@Valid @RequestBody DeployTestDataRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				token -> problemService.deploy(token, problemId, problemVersionId, request, requestId))
				.map(deployment -> ok(deployment, requestId));
	}

	@PostMapping("/{problemId}/versions/{problemVersionId}/calibration")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.LanguageCalibration>>> calibrate(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@PathVariable @Pattern(regexp = UUID) String problemVersionId,
			@Valid @RequestBody CalibrateProblemRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				token -> problemService.calibrate(token, problemId, problemVersionId, request, requestId))
				.map(calibration -> ok(calibration, requestId));
	}

	@GetMapping("/{problemId}/versions/{problemVersionId}/publish-check")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.PublishCheck>>> publishCheck(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@PathVariable @Pattern(regexp = UUID) String problemVersionId,
			ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return adminRead(exchange, requestId, token -> problemService
				.publishCheck(token, problemId, problemVersionId, requestId))
				.map(check -> ok(check, requestId));
	}

	@PostMapping("/{problemId}/versions/{problemVersionId}/publish")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.AdminProblemVersion>>> publish(
			@PathVariable @Pattern(regexp = UUID) String problemId,
			@PathVariable @Pattern(regexp = UUID) String problemVersionId,
			@Valid @RequestBody RowVersionRequest request, ServerWebExchange exchange) {
		String requestId = requestId(exchange);
		return admin(exchange, requestId,
				token -> problemService.publish(token, problemId, problemVersionId, request, requestId))
				.map(version -> ok(version, requestId));
	}

	private <T> Mono<T> admin(
			ServerWebExchange exchange, String requestId, Function<String, Mono<T>> action) {
		return adminAccess.accessToken(exchange, requestId).flatMap(action)
				.onErrorMap(error -> ProblemApiErrors.map(error, false));
	}

	private <T> Mono<T> adminRead(
			ServerWebExchange exchange, String requestId, Function<String, Mono<T>> action) {
		return adminAccess.readWithRecovery(
				exchange,
				requestId,
				action,
				AdminProblemsController::isUnauthorized,
				error -> ProblemApiErrors.map(error, false));
	}

	private static boolean isUnauthorized(Throwable error) {
		return error instanceof ProblemServiceClientException upstream
				&& upstream.status().value() == HttpStatus.UNAUTHORIZED.value();
	}

	private static String requestId(ServerWebExchange exchange) {
		return ApiRequestContext.requestId(exchange);
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

	record UpdateProblemRequest(
			@NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 128) String slug,
			@NotBlank @Pattern(regexp = "^(PRIVATE|PUBLIC)$") String visibility,
			@Min(0) long rowVersion) {
	}

	record RowVersionRequest(@Min(0) long rowVersion) {
	}

	record CreateRevisionRequest(@Min(0) long rowVersion, boolean reuseTestData) {
	}

	record SampleInput(
			@Min(1) @Max(100) int ordinal,
			@NotNull @Size(max = 1_048_576) String input,
			@NotNull @Size(max = 1_048_576) String output,
			@Size(max = 1_048_576) String explanationMarkdown) {
	}

	record UpdateVersionRequest(
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
			@Size(max = 8192) String changeSummary,
			@Min(0) long rowVersion) {
	}

	record BindTestDataRequest(
			@NotBlank @Pattern(regexp = UUID) String testDataVersionId,
			@Min(0) long rowVersion) {
	}

	record DeployTestDataRequest(
			@NotBlank @Pattern(regexp = UUID) String testDataVersionId,
			@NotBlank @Pattern(regexp = SHA256) String expectedSha256,
			@Min(0) long rowVersion) {
	}

	record CalibrateProblemRequest(
			@NotBlank @Pattern(regexp = "^cpp$") String languageId,
			@Min(1) long cpuNs,
			@Min(1) long memoryBytes,
			@Min(1) Long clockNs,
			@NotBlank @Size(max = 1_048_576) String referenceSource,
			@Min(0) long rowVersion) {
	}
}
