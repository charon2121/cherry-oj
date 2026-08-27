package com.cherryoj.gatewayservice.auth;

import org.springframework.http.HttpStatusCode;

final class UserServiceClientException extends RuntimeException {

	private final HttpStatusCode status;
	private final String code;

	UserServiceClientException(HttpStatusCode status, String code) {
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
