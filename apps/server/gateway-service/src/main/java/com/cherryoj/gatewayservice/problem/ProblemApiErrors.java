package com.cherryoj.gatewayservice.problem;

import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.cherryoj.gatewayservice.api.ApiProblemException;

final class ProblemApiErrors {

	private static final Set<String> SAFE_CLIENT_CODES = Set.of(
			"VALIDATION_FAILED", "MALFORMED_REQUEST", "INVALID_QUERY", "INVALID_CURSOR",
			"PROBLEM_NOT_FOUND",
			"PROBLEM_VERSION_NOT_FOUND", "TEST_DATA_NOT_FOUND", "ROW_VERSION_CONFLICT",
			"RESOURCE_STATE_CONFLICT", "SLUG_CONFLICT", "PAYLOAD_TOO_LARGE",
			"UNSUPPORTED_MEDIA_TYPE", "TEST_DATA_EMPTY", "INVALID_TEST_DATA_ARCHIVE",
			"JUDGING_STATE_CONFLICT", "JUDGING_VALIDATION_FAILED");

	private ProblemApiErrors() {
	}

	static ApiProblemException map(Throwable error, boolean publicApi) {
		if (error instanceof ApiProblemException problem) {
			return problem;
		}
		if (error instanceof TimeoutException) {
			return gatewayTimeout();
		}
		if (error instanceof WebClientRequestException) {
			return unavailable();
		}
		if (error instanceof DecodingException || error instanceof DataBufferLimitException) {
			return badGateway();
		}
		if (error instanceof ProblemServiceClientException upstream) {
			int status = upstream.status().value();
			if (status >= 500) {
				return status == 504 ? gatewayTimeout() : unavailable();
			}
			if (publicApi && status == 404) {
				return "PROBLEM_NOT_FOUND".equals(upstream.code())
						? client(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND") : badGateway();
			}
			if (status == 401 || status == 403 || !SAFE_CLIENT_CODES.contains(upstream.code())) {
				return badGateway();
			}
			HttpStatus clientStatus = HttpStatus.resolve(status);
			return clientStatus != null && clientStatus.is4xxClientError()
					? client(clientStatus, upstream.code()) : badGateway();
		}
		return badGateway();
	}

	private static ApiProblemException client(HttpStatus status, String code) {
		return new ApiProblemException(status, code, switch (status) {
			case BAD_REQUEST -> "请求格式错误";
			case NOT_FOUND -> "资源不存在";
			case CONFLICT -> "资源状态冲突";
			case CONTENT_TOO_LARGE -> "请求体过大";
			case UNSUPPORTED_MEDIA_TYPE -> "媒体类型不受支持";
			case UNPROCESSABLE_CONTENT -> "请求参数校验失败";
			default -> "请求失败";
		}, switch (status) {
			case NOT_FOUND -> "请求的资源不存在。";
			case CONFLICT -> "资源当前状态不允许此操作。";
			case CONTENT_TOO_LARGE -> "请求体超过允许大小。";
			case UNSUPPORTED_MEDIA_TYPE -> "请使用受支持的请求格式。";
			case UNPROCESSABLE_CONTENT -> "请检查请求内容。";
			default -> "请求无法完成。";
		});
	}

	private static ApiProblemException unavailable() {
		return new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
				"服务暂不可用", "服务暂时不可用，请稍后重试。");
	}

	private static ApiProblemException gatewayTimeout() {
		return new ApiProblemException(HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT",
				"上游响应超时", "服务暂时不可用，请稍后重试。");
	}

	private static ApiProblemException badGateway() {
		return new ApiProblemException(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY",
				"上游响应无效", "服务暂时不可用，请稍后重试。");
	}
}
