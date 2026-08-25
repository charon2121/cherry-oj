package com.cherryoj.gatewayservice.api;

import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiMeta(String requestId, ApiPagination pagination) {

	private static final Pattern REQUEST_ID_PATTERN =
			Pattern.compile("^req_[A-Za-z0-9_-]{16,64}$");

	public ApiMeta {
		if (requestId == null || !REQUEST_ID_PATTERN.matcher(requestId).matches()) {
			throw new IllegalArgumentException("requestId is outside the public contract");
		}
	}

	public ApiMeta(String requestId) {
		this(requestId, null);
	}

}
