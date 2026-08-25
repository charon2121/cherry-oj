package com.cherryoj.gatewayservice.api;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatusCode;

public final class ApiProblemException extends RuntimeException {

	private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

	private final HttpStatusCode status;
	private final String code;
	private final String title;
	private final List<FieldViolation> violations;

	public ApiProblemException(HttpStatusCode status, String code, String title, String detail) {
		this(status, code, title, detail, List.of());
	}

	public ApiProblemException(
			HttpStatusCode status,
			String code,
			String title,
			String detail,
			List<FieldViolation> violations) {
		super(detail);
		if (!status.isError()) {
			throw new IllegalArgumentException("ApiProblemException status must be 4xx or 5xx");
		}
		if (code == null || !CODE_PATTERN.matcher(code).matches()) {
			throw new IllegalArgumentException("ApiProblemException code must be stable upper snake case");
		}
		if (title == null || title.isBlank() || title.length() > 256) {
			throw new IllegalArgumentException("ApiProblemException title must contain 1 to 256 characters");
		}
		if (detail == null || detail.isBlank() || detail.length() > 2048) {
			throw new IllegalArgumentException("ApiProblemException detail must contain 1 to 2048 characters");
		}
		this.status = status;
		this.code = code;
		this.title = title;
		this.violations = List.copyOf(violations);
	}

	public HttpStatusCode status() {
		return status;
	}

	public String code() {
		return code;
	}

	public String title() {
		return title;
	}

	public List<FieldViolation> violations() {
		return violations;
	}

}
