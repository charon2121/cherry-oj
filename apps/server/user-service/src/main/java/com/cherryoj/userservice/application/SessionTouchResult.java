package com.cherryoj.userservice.application;

import java.time.LocalDateTime;

public record SessionTouchResult(
        LocalDateTime sessionAbsoluteExpiresAt,
        long sessionAbsoluteTimeoutSeconds,
        String sessionLifetimePolicy) {
}
