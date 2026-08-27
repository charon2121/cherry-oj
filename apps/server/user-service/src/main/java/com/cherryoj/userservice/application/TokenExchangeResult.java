package com.cherryoj.userservice.application;

import java.time.Instant;
import java.time.LocalDateTime;

public record TokenExchangeResult(
        UserView user,
        String accessToken,
        Instant accessTokenExpiresAt,
        LocalDateTime sessionIdleExpiresAt,
        LocalDateTime sessionAbsoluteExpiresAt,
        long sessionIdleTimeoutSeconds,
        long sessionAbsoluteTimeoutSeconds,
        boolean sessionRefreshIdleOnActivity) {
}
