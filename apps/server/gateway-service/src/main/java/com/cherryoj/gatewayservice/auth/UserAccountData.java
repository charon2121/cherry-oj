package com.cherryoj.gatewayservice.auth;

import java.io.Serializable;
import java.time.Instant;

public record UserAccountData(
		String id,
		String username,
		String role,
		String status,
		boolean passwordChangeRequired,
		Instant createdAt,
		Instant updatedAt,
		long rowVersion) implements Serializable {
}
