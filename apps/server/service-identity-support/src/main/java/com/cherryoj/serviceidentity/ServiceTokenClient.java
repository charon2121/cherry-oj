package com.cherryoj.serviceidentity;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调用方使用：为某个目标服务取一个服务令牌，放进 Authorization: Bearer。
 *
 * <p>令牌按目标服务缓存，剩余寿命不足 {@link ServiceTokenClaims#REFRESH_AHEAD} 时才向 user-service 重新换取，
 * 所以 user-service 短暂不可用不会立刻打断判题；同一目标的并发换取只发一次请求。
 */
public final class ServiceTokenClient {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
	private static final int MAX_RESPONSE_CHARS = 16 * 1024;

	private final String name;
	private final RSAPrivateKey key;
	private final String issuer;
	private final URI tokenUri;
	private final Clock clock;
	private final HttpClient http;
	private final Map<String, Cached> cache = new ConcurrentHashMap<>();
	private final Map<String, Object> locks = new ConcurrentHashMap<>();

	public ServiceTokenClient(String name, RSAPrivateKey key, String issuer, URI tokenUri, Clock clock) {
		this(name, key, issuer, tokenUri, clock, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
				.followRedirects(HttpClient.Redirect.NEVER).build());
	}

	ServiceTokenClient(String name, RSAPrivateKey key, String issuer, URI tokenUri, Clock clock, HttpClient http) {
		this.name = ServiceNames.require(name, "service identity name");
		this.key = java.util.Objects.requireNonNull(key, "service private key");
		if (issuer == null || issuer.isBlank()) throw new IllegalArgumentException("identity issuer is required");
		this.issuer = issuer;
		if (tokenUri == null || !tokenUri.isAbsolute()) throw new IllegalArgumentException("service token URI must be absolute");
		this.tokenUri = tokenUri;
		this.clock = clock;
		this.http = http;
	}

	public String name() {
		return name;
	}

	public String tokenFor(String audience) {
		ServiceNames.require(audience, "audience");
		Cached current = cache.get(audience);
		if (fresh(current)) return current.token();
		synchronized (locks.computeIfAbsent(audience, ignored -> new Object())) {
			current = cache.get(audience);
			if (fresh(current)) return current.token();
			Cached fetched = fetch(audience);
			cache.put(audience, fetched);
			return fetched.token();
		}
	}

	/** 被调方拒绝了缓存的令牌（例如签发方换了密钥）时丢弃它，下一次重新换取。 */
	public void invalidate(String audience) {
		cache.remove(audience);
	}

	private boolean fresh(Cached cached) {
		return cached != null && cached.expiresAt().minus(ServiceTokenClaims.REFRESH_AHEAD).isAfter(clock.instant());
	}

	private Cached fetch(String audience) {
		String assertion = ClientAssertion.sign(name, key, issuer, clock.instant());
		var request = HttpRequest.newBuilder(tokenUri).timeout(REQUEST_TIMEOUT)
				.header("Authorization", "Bearer " + assertion)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(JSONObjectUtils.toJSONString(Map.of("audience", audience))))
				.build();
		HttpResponse<String> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (IOException error) {
			throw new ServiceIdentityException("TOKEN_ENDPOINT_UNAVAILABLE",
					name + " cannot reach " + tokenUri + " for a " + audience + " token: " + error.getClass().getSimpleName(), error);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new ServiceIdentityException("TOKEN_ENDPOINT_UNAVAILABLE", "interrupted while requesting a service token", error);
		}
		String body = response.body() == null ? "" : response.body();
		if (response.statusCode() != 200) {
			throw new ServiceIdentityException("TOKEN_REQUEST_REJECTED", name + " was refused a " + audience
					+ " token: status " + response.statusCode() + " " + snippet(body));
		}
		try {
			var json = JSONObjectUtils.parse(body.length() > MAX_RESPONSE_CHARS ? "" : body);
			String token = JSONObjectUtils.getString(json, "accessToken");
			Instant expiresAt = Instant.parse(JSONObjectUtils.getString(json, "expiresAt"));
			if (token == null || token.isBlank()) throw new ParseException("accessToken missing", 0);
			return new Cached(token, expiresAt);
		}
		catch (ParseException | DateTimeParseException | NullPointerException error) {
			throw new ServiceIdentityException("TOKEN_RESPONSE_INVALID", "service token response for " + audience + " is malformed", error);
		}
	}

	private static String snippet(String body) {
		String flat = body.replaceAll("\\s+", " ");
		return flat.length() > 200 ? flat.substring(0, 200) + "…" : flat;
	}

	private record Cached(String token, Instant expiresAt) {
	}
}
