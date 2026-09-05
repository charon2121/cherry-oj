package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.cherryoj.gatewayservice.api.ApiProblemException;

class AdminGatewayAccessTests {
	private static final String REQUEST_ID = "req_0123456789abcdef0123456789abcdef";

	@Test
	void rejectsPasswordChangeRequiredBeforeRoleAuthorization() {
		assertThatThrownBy(() -> AdminGatewayAccess.requireAdmin(state("ADMIN", true), REQUEST_ID))
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.code()).isEqualTo("PASSWORD_CHANGE_REQUIRED"));
	}

	@Test
	void rejectsUserAndReturnsOnlyAdminDelegatedToken() {
		assertThatThrownBy(() -> AdminGatewayAccess.requireAdmin(state("USER", false), REQUEST_ID))
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));

		DelegatedIdentity identity = AdminGatewayAccess.requireAdmin(state("ADMIN", false), REQUEST_ID);
		assertThat(identity.accessToken()).isEqualTo("delegated-access-token");
		assertThat(identity.requestId()).isEqualTo(REQUEST_ID);
		assertThat(identity.toString()).doesNotContain("delegated-access-token");
	}

	private static AuthSessionState state(String role, boolean passwordChangeRequired) {
		Instant now = Instant.parse("2026-08-30T00:00:00Z");
		return new AuthSessionState(
				new UserAccountData("user-id", "admin", role, "ACTIVE", passwordChangeRequired,
						now, now, 1),
				"grant", "delegated-access-token", now.plusSeconds(300), now.plus(Duration.ofDays(30)));
	}
}
