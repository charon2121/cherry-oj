package com.cherryoj.gatewayservice.api;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public final class ApiProblemHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiProblemHandler.class);
	private static final String INTERNAL_ERROR_DETAIL = "服务暂时不可用，请稍后重试。";

	@ExceptionHandler(ApiProblemException.class)
	ResponseEntity<ProblemDetail> handleApiProblem(
			ApiProblemException error, ServerWebExchange exchange) {
		return response(
				error.status(),
				error.code(),
				error.title(),
				error.getMessage(),
				error.violations(),
				exchange);
	}

	@ExceptionHandler(WebExchangeBindException.class)
	ResponseEntity<ProblemDetail> handleValidation(
			WebExchangeBindException error, ServerWebExchange exchange) {
		List<FieldViolation> violations = error.getBindingResult().getAllErrors().stream()
				.limit(100)
				.map(objectError -> {
					String path = objectError instanceof FieldError fieldError
							? fieldError.getField()
							: "$";
					String code = normalizeViolationCode(objectError.getCode());
					return new FieldViolation(path, code, violationMessage(code));
				})
				.toList();
		return response(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"VALIDATION_FAILED",
				"请求参数校验失败",
				"请检查标记字段。",
				violations,
				exchange);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ProblemDetail> handleConstraintViolation(
			ConstraintViolationException error, ServerWebExchange exchange) {
		List<FieldViolation> violations = error.getConstraintViolations().stream()
				.limit(100)
				.map(violation -> {
					String code = constraintViolationCode(violation);
					return new FieldViolation(
							constraintViolationPath(violation),
							code,
							violationMessage(code));
				})
				.toList();
		return response(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"VALIDATION_FAILED",
				"请求参数校验失败",
				"请检查标记字段。",
				violations,
				exchange);
	}

	@ExceptionHandler(ServerWebInputException.class)
	ResponseEntity<ProblemDetail> handleMalformedRequest(
			ServerWebInputException error, ServerWebExchange exchange) {
		return response(
				HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST",
				"请求格式错误",
				"请求格式无效。",
				List.of(),
				exchange);
	}

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ProblemDetail> handleHttpStatus(
			ResponseStatusException error, ServerWebExchange exchange) {
		ProblemDescription description = describe(error.getStatusCode());
		return response(
				error.getStatusCode(),
				description.code(),
				description.title(),
				description.detail(),
				List.of(),
				exchange);
	}

	@ExceptionHandler(Throwable.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Throwable error, ServerWebExchange exchange) {
		String requestId = ApiRequestContext.requestId(exchange);
		LOGGER.error(
				"Unhandled browser API error requestId={} errorType={}",
				requestId,
				error.getClass().getName());
		return response(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_ERROR",
				"服务器内部错误",
				INTERNAL_ERROR_DETAIL,
				List.of(),
				exchange);
	}

	private static ResponseEntity<ProblemDetail> response(
			HttpStatusCode status,
			String code,
			String title,
			String detail,
			List<FieldViolation> violations,
			ServerWebExchange exchange) {
		String requestId = ApiRequestContext.requestId(exchange);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("urn:cherry-oj:problem:" + problemType(code)));
		problem.setTitle(title);
		problem.setInstance(URI.create("urn:cherry-oj:request:" + requestId));
		problem.setProperty("code", code);
		problem.setProperty("meta", new ApiMeta(requestId));
		if (!violations.isEmpty()) {
			problem.setProperty("violations", violations);
		}

		return ResponseEntity.status(status)
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.header(ApiRequestContext.REQUEST_ID_HEADER, requestId)
				.body(problem);
	}

	private static String normalizeViolationCode(String code) {
		if (code == null || code.isBlank()) {
			return "INVALID";
		}
		String normalized = code.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
				.replaceAll("[^A-Za-z0-9]+", "_")
				.replaceAll("^_+|_+$", "")
				.toUpperCase(Locale.ROOT);
		return normalized.isEmpty() || normalized.length() > 64 ? "INVALID" : normalized;
	}

	private static String constraintViolationCode(ConstraintViolation<?> violation) {
		if (violation.getConstraintDescriptor() == null
				|| violation.getConstraintDescriptor().getAnnotation() == null) {
			return "INVALID";
		}
		return normalizeViolationCode(
				violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
	}

	private static String constraintViolationPath(ConstraintViolation<?> violation) {
		String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
		int separator = path.lastIndexOf('.');
		String field = separator >= 0 ? path.substring(separator + 1) : path;
		return field.isBlank() ? "$" : field;
	}

	private static String violationMessage(String code) {
		return switch (code) {
			case "NOT_BLANK", "NOT_EMPTY", "NOT_NULL" -> "字段不能为空。";
			case "SIZE" -> "字段长度或数量不符合约束。";
			case "MIN", "MAX", "DECIMAL_MIN", "DECIMAL_MAX", "POSITIVE", "NEGATIVE" ->
				"字段数值不符合约束。";
			case "EMAIL", "PATTERN" -> "字段格式不正确。";
			default -> "字段不符合约束。";
		};
	}

	private static String problemType(String code) {
		return code.toLowerCase(Locale.ROOT).replace('_', '-');
	}

	private static ProblemDescription describe(HttpStatusCode statusCode) {
		HttpStatus status = HttpStatus.resolve(statusCode.value());
		if (status == null) {
			return new ProblemDescription("HTTP_ERROR", "请求失败", "请求无法完成。");
		}

		return switch (status) {
			case BAD_REQUEST -> new ProblemDescription(
					"MALFORMED_REQUEST", "请求格式错误", "请求格式无效。");
			case UNAUTHORIZED -> new ProblemDescription(
					"UNAUTHENTICATED", "未认证", "请先完成登录认证。");
			case FORBIDDEN -> new ProblemDescription(
					"FORBIDDEN", "无权访问", "当前身份无权执行此操作。");
			case NOT_FOUND -> new ProblemDescription(
					"NOT_FOUND", "资源不存在", "请求的资源不存在。");
			case METHOD_NOT_ALLOWED -> new ProblemDescription(
					"METHOD_NOT_ALLOWED", "请求方法不受支持", "该资源不支持当前请求方法。");
			case CONFLICT -> new ProblemDescription(
					"CONFLICT", "资源状态冲突", "资源当前状态不允许此操作。");
			case PRECONDITION_FAILED -> new ProblemDescription(
					"PRECONDITION_FAILED", "前置条件失败", "请求的前置条件不成立。");
			case CONTENT_TOO_LARGE -> new ProblemDescription(
					"PAYLOAD_TOO_LARGE", "请求体过大", "请求体超过允许大小。");
			case UNSUPPORTED_MEDIA_TYPE -> new ProblemDescription(
					"UNSUPPORTED_MEDIA_TYPE", "媒体类型不受支持", "请使用受支持的请求格式。");
			case UNPROCESSABLE_CONTENT -> new ProblemDescription(
					"VALIDATION_FAILED", "请求参数校验失败", "请检查标记字段。");
			case TOO_MANY_REQUESTS -> new ProblemDescription(
					"RATE_LIMITED", "请求过于频繁", "请稍后重试。");
			case BAD_GATEWAY -> new ProblemDescription(
					"BAD_GATEWAY", "上游响应无效", INTERNAL_ERROR_DETAIL);
			case SERVICE_UNAVAILABLE -> new ProblemDescription(
					"SERVICE_UNAVAILABLE", "服务暂不可用", INTERNAL_ERROR_DETAIL);
			case GATEWAY_TIMEOUT -> new ProblemDescription(
					"GATEWAY_TIMEOUT", "上游响应超时", INTERNAL_ERROR_DETAIL);
			default -> status.is5xxServerError()
					? new ProblemDescription("INTERNAL_ERROR", "服务器内部错误", INTERNAL_ERROR_DETAIL)
					: new ProblemDescription("HTTP_ERROR", "请求失败", "请求无法完成。");
		};
	}

	private record ProblemDescription(String code, String title, String detail) {
	}

}
