package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.identity.token.CallUser;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdminGatewayAccessTests {
    private static final String USER_ID = "019c8e42-7f70-7000-8000-000000000001";
    private static final String REQUEST_ID = "req_0123456789abcdef0123456789abcdef";

    @Test
    void rejectsPasswordChangeRequiredBeforeRoleAuthorization() {
        assertThatThrownBy(() -> AdminGatewayAccess.requireAdmin(state("ADMIN", true), REQUEST_ID))
                .isInstanceOfSatisfying(
                        ApiProblemException.class,
                        error -> assertThat(error.code()).isEqualTo("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void rejectsUserAndActsOnlyForAnAdmin() {
        assertThatThrownBy(() -> AdminGatewayAccess.requireAdmin(state("USER", false), REQUEST_ID))
                .isInstanceOfSatisfying(
                        ApiProblemException.class,
                        error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));

        ActingUser identity = AdminGatewayAccess.requireAdmin(state("ADMIN", false), REQUEST_ID);
        assertThat(identity.user()).isEqualTo(new CallUser(USER_ID, "ADMIN"));
        assertThat(identity.requestId()).isEqualTo(REQUEST_ID);
    }

    private static AuthSessionState state(String role, boolean passwordChangeRequired) {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        return new AuthSessionState(
                new UserAccountData(USER_ID, "admin", role, "ACTIVE", passwordChangeRequired, now, now, 1),
                "s".repeat(43),
                now.plus(Duration.ofDays(30)),
                now);
    }
}
