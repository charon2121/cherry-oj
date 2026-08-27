package com.cherryoj.userservice.application;

import java.time.LocalDateTime;

public record SessionTouchResult(
        LocalDateTime sessionIdleExpiresAt,
        LocalDateTime sessionAbsoluteExpiresAt,
        long sessionIdleTimeoutSeconds,
        long sessionAbsoluteTimeoutSeconds,
        boolean sessionRefreshIdleOnActivity) {
}
