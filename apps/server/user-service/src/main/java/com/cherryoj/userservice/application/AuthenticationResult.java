package com.cherryoj.userservice.application;

import java.time.Instant;

/** authenticate 的成功响应（协议 §5）。 */
public record AuthenticationResult(String sessionId, UserView user, Instant sessionExpiresAt) {}
