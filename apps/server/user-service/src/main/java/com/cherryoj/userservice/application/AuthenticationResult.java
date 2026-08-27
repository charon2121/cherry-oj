package com.cherryoj.userservice.application;

import java.time.Instant;
import java.time.LocalDateTime;

public record AuthenticationResult(
        UserView user,
        String loginGrant,
        String accessToken,
        Instant accessTokenExpiresAt,
        LocalDateTime sessionIdleExpiresAt,
        LocalDateTime sessionAbsoluteExpiresAt) {
}
