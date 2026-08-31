package com.cherryoj.gatewayservice.problem;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.api.ApiSuccess;
import com.cherryoj.gatewayservice.api.CursorPagination;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/problems")
@Validated
public class ProblemsController {

	private final ProblemServiceClient problemService;

	ProblemsController(ProblemServiceClient problemService) {
		this.problemService = problemService;
	}

	@GetMapping
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.ProblemListData>>> list(
			@RequestParam(required = false) @Size(min = 1, max = 100) String q,
			@RequestParam(required = false) @Pattern(regexp = "^(UNRATED|EASY|MEDIUM|HARD)$")
			String difficulty,
			@RequestParam(required = false) @Size(max = 10)
			List<@Size(min = 1, max = 32) String> tag,
			@RequestParam(required = false) @Pattern(regexp = "^(ACM|CORE)$") String codeMode,
			@RequestParam(required = false) @Pattern(regexp = "^[a-z][a-z0-9-]{0,31}$")
			String language,
			@RequestParam(defaultValue = "UPDATED_DESC")
			@Pattern(regexp = "^(UPDATED_DESC|UPDATED_ASC|TITLE_ASC)$") String sort,
			@RequestParam(required = false) @Size(min = 1, max = 2048) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			ServerWebExchange exchange) {
		String requestId = ApiRequestContext.requestId(exchange);
		// Deliberately does not resolve a WebSession: public browsing must not depend on Redis/auth.
		return problemService.listPublic(
				q, difficulty, tag, codeMode, language, sort, cursor, size, requestId)
				.map(ProblemsController::validateList)
				.onErrorMap(error -> ProblemApiErrors.map(error, true))
				.map(result -> ResponseEntity.ok().body(ApiSuccess.of(
						new ProblemDtos.ProblemListData(result.items()), requestId,
						new CursorPagination(result.nextCursor(), result.hasMore()))));
	}

	@GetMapping("/{slug}")
	Mono<ResponseEntity<ApiSuccess<ProblemDtos.ProblemDetail>>> get(
			@PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
			@Size(max = 128) String slug,
			ServerWebExchange exchange) {
		String requestId = ApiRequestContext.requestId(exchange);
		return problemService.getPublic(slug, requestId)
				.map(ProblemsController::validateDetail)
				.onErrorMap(error -> ProblemApiErrors.map(error, true))
				.map(detail -> ResponseEntity.ok().body(ApiSuccess.of(detail, requestId)));
	}

	private static ProblemDtos.ProblemList validateList(ProblemDtos.ProblemList result) {
		if (result == null || result.items() == null || result.items().size() > 100
				|| result.items().stream().anyMatch(item -> item == null || item.problemId() == null
						|| item.slug() == null || item.currentVersionId() == null || item.title() == null
						|| item.tags() == null || item.allowedLanguages() == null)
				|| (result.hasMore() && result.nextCursor() == null)
				|| (!result.hasMore() && result.nextCursor() != null)) {
			throw new IllegalStateException("invalid public problem list contract");
		}
		return result;
	}

	private static ProblemDtos.ProblemDetail validateDetail(ProblemDtos.ProblemDetail detail) {
		if (detail == null || detail.problemId() == null || detail.problemVersionId() == null
				|| detail.slug() == null || detail.codeMode() == null || detail.title() == null
				|| detail.difficulty() == null || detail.tags() == null
				|| detail.statementMarkdown() == null || detail.inputDescriptionMarkdown() == null
				|| detail.outputDescriptionMarkdown() == null || detail.samples() == null
				|| detail.allowedLanguages() == null) {
			throw new IllegalStateException("invalid public problem detail contract");
		}
		return detail;
	}
}
