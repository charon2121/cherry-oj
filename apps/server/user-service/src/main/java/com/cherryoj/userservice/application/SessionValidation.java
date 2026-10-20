package com.cherryoj.userservice.application;

import java.time.Instant;

/** validate 的成功响应（协议 §5）。 */
public record SessionValidation(UserView user, Instant sessionExpiresAt) {}
