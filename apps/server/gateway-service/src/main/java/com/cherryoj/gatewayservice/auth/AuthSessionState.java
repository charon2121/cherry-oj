package com.cherryoj.gatewayservice.auth;

import java.io.Serializable;
import java.time.Instant;

/** 浏览器会话里保存的登录状态：sessionId 只在 gateway 与 user-service 之间使用，不发给浏览器。 */
record AuthSessionState(UserAccountData user, String sessionId, Instant sessionExpiresAt, Instant validatedAt)
        implements Serializable {}
