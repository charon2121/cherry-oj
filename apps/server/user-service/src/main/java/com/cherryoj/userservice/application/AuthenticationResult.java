package com.cherryoj.userservice.application;

import java.time.Instant;
import java.time.LocalDateTime;

public record AuthenticationResult(
        UserView user,
        String loginGrant,
        String accessToken,
        Instant accessTokenExpiresAt,
        LocalDateTime sessionAbsoluteExpiresAt,
        long sessionAbsoluteTimeoutSeconds,
        String sessionLifetimePolicy) {
}
