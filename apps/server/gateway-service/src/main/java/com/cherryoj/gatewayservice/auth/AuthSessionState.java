package com.cherryoj.gatewayservice.auth;

import java.io.Serializable;
import java.time.Instant;

record AuthSessionState(
		UserAccountData user,
		String loginGrant,
		String accessToken,
		Instant accessTokenExpiresAt,
		Instant absoluteExpiresAt) implements Serializable {
}
