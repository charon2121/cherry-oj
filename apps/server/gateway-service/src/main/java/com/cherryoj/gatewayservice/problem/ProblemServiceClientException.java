package com.cherryoj.gatewayservice.problem;

import org.springframework.http.HttpStatusCode;

final class ProblemServiceClientException extends RuntimeException {

	private final HttpStatusCode status;
	private final String code;

	ProblemServiceClientException(HttpStatusCode status, String code) {
		super(code);
		this.status = status;
		this.code = code;
	}

	HttpStatusCode status() {
		return status;
	}

	String code() {
		return code;
	}
}
