package com.cherryoj.gatewayservice.auth;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.cherryoj.gatewayservice.api.ApiProblemException;

@Component
final class LoginRateLimiter {

	private static final int MAX_KEYS = 10_000;

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
	private final AtomicLong calls = new AtomicLong();
	private final int limit;
	private final Clock clock;

	@Autowired
	LoginRateLimiter(GatewayAuthProperties properties) {
		this(properties.loginRateLimitPerMinute(), Clock.systemUTC());
	}

	LoginRateLimiter(int limit, Clock clock) {
		this.limit = limit;
		this.clock = clock;
	}

	void check(ServerWebExchange exchange) {
		Instant now = clock.instant();
		String key = source(exchange);
		Window window = windows.compute(key, (ignored, current) -> {
			if (current == null || !now.isBefore(current.startedAt().plus(1, ChronoUnit.MINUTES))) {
				return new Window(now, 1);
			}
			return new Window(current.startedAt(), current.count() + 1);
		});

		if ((calls.incrementAndGet() & 255) == 0 || windows.size() > MAX_KEYS) {
			windows.entrySet().removeIf(entry ->
					!now.isBefore(entry.getValue().startedAt().plus(2, ChronoUnit.MINUTES)));
		}
		if (window.count() > limit) {
			long remainingMillis = Duration.between(
					now, window.startedAt().plus(1, ChronoUnit.MINUTES)).toMillis();
			long retryAfterSeconds = Math.max(1, (remainingMillis + 999) / 1_000);
			exchange.getResponse().getHeaders()
					.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
			throw new ApiProblemException(
					HttpStatus.TOO_MANY_REQUESTS,
					"RATE_LIMITED",
					"请求过于频繁",
					"请稍后重试。");
		}
	}

	private static String source(ServerWebExchange exchange) {
		InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
		return remote == null ? "unknown" : remote.getAddress().getHostAddress();
	}

	private record Window(Instant startedAt, int count) {
	}
}
