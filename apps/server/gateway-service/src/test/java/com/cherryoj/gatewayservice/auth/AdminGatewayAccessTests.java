package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.cherryoj.gatewayservice.api.ApiProblemException;

class AdminGatewayAccessTests {

	@Test
	void rejectsPasswordChangeRequiredBeforeRoleAuthorization() {
		assertThatThrownBy(() -> AdminGatewayAccess.requireAdmin(state("ADMIN", true)))
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.code()).isEqualTo("PASSWORD_CHANGE_REQUIRED"));
	}

	@Test
	void rejectsUserAndReturnsOnlyAdminDelegatedToken() {
		assertThatThrownBy(() -> AdminGatewayAccess.requireAdmin(state("USER", false)))
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));

		assertThat(AdminGatewayAccess.requireAdmin(state("ADMIN", false)))
				.isEqualTo("delegated-access-token");
	}

	private static AuthSessionState state(String role, boolean passwordChangeRequired) {
		Instant now = Instant.parse("2026-08-30T00:00:00Z");
		return new AuthSessionState(
				new UserAccountData("user-id", "admin", role, "ACTIVE", passwordChangeRequired,
						now, now, 1),
				"grant", "delegated-access-token", now.plusSeconds(300),
				now.plusSeconds(1_800), now.plusSeconds(43_200));
	}
}
