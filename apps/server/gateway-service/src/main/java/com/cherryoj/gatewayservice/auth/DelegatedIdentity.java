package com.cherryoj.gatewayservice.auth;

import java.time.Instant;

/** Immutable identity proof handed to internal HTTP clients after session validation. */
public record DelegatedIdentity(String accessToken, Instant expiresAt, String requestId) {

	public DelegatedIdentity {
		if (accessToken == null || accessToken.isBlank()) {
			throw new IllegalArgumentException("accessToken must not be blank");
		}
		if (expiresAt == null) {
			throw new IllegalArgumentException("expiresAt must not be null");
		}
		if (requestId == null || !requestId.matches("^req_[0-9a-f]{32}$")) {
			throw new IllegalArgumentException("requestId is invalid");
		}
	}

	@Override
	public String toString() {
		return "DelegatedIdentity[accessToken=<redacted>, expiresAt=" + expiresAt
				+ ", requestId=" + requestId + "]";
	}
}
