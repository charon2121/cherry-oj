package com.cherryoj.gatewayservice.problem;

import org.springframework.http.HttpStatusCode;

final class ProblemServiceClientException extends RuntimeException {

	private final HttpStatusCode status;
	private final String code;
	private final String detail;

	ProblemServiceClientException(HttpStatusCode status, String code) {
		this(status, code, null);
	}

	ProblemServiceClientException(HttpStatusCode status, String code, String detail) {
		super(code);
		this.status = status;
		this.code = code;
		this.detail = detail;
	}

	HttpStatusCode status() {
		return status;
	}

	String code() {
		return code;
	}

	String detail() {
		return detail;
	}
}
