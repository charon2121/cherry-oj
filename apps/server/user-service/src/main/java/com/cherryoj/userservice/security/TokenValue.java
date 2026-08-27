package com.cherryoj.userservice.security;

import java.time.Instant;

public record TokenValue(String value, Instant expiresAt) {
}
