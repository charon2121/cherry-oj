package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.session.Session;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class GatewaySessionRedisIntegrationTests {

	private static final Pattern TOKEN_PATTERN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");
	private static final AtomicInteger AUTHENTICATE_CALLS = new AtomicInteger();
	private static final AtomicInteger TOKEN_EXCHANGE_CALLS = new AtomicInteger();
	private static final AtomicInteger ADMIN_USER_CALLS = new AtomicInteger();
	private static volatile LocalDateTime sessionAbsoluteExpiresAt;
	private static final DisposableServer USER_SERVICE = startUserService();

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(
			DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

	@LocalServerPort
	private int port;

	private final ReactiveStringRedisTemplate redis;
	private final ReactiveRedisSessionRepository sessions;

	@Autowired
	GatewaySessionRedisIntegrationTests(
			ReactiveStringRedisTemplate redis, ReactiveRedisSessionRepository sessions) {
		this.redis = redis;
		this.sessions = sessions;
	}

	private static DisposableServer startUserService() {
		return HttpServer.create().host("127.0.0.1").port(0).route(routes -> routes
				.post("/internal/auth/authenticate", (request, response) -> {
					AUTHENTICATE_CALLS.incrementAndGet();
					return request.receive().aggregate().then(response.status(HttpStatus.OK.value())
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.sendString(Mono.just(authenticationJson())).then());
				})
				.post("/internal/auth/touch", (request, response) -> request.receive().aggregate()
						.then(response.status(HttpStatus.OK.value())
								.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
								.sendString(Mono.just(touchJson())).then()))
				.post("/internal/auth/token", (request, response) -> {
					TOKEN_EXCHANGE_CALLS.incrementAndGet();
					return request.receive().aggregate().then(response.status(HttpStatus.OK.value())
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.sendString(Mono.just(tokenExchangeJson())).then());
				})
				.post("/internal/auth/revoke", (request, response) -> request.receive().aggregate()
						.then(response.status(HttpStatus.NO_CONTENT.value()).send()))
				.get("/internal/admin/users", (request, response) -> {
					ADMIN_USER_CALLS.incrementAndGet();
					String authorization = request.requestHeaders().get(HttpHeaders.AUTHORIZATION);
					if ("Bearer fresh-internal-jwt-canary".equals(authorization)) {
						return response.status(HttpStatus.OK.value())
								.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
								.sendString(Mono.just(userPageJson())).then();
					}
					return response.status(HttpStatus.UNAUTHORIZED.value())
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.sendString(Mono.just("{\"code\":\"INVALID_TOKEN\",\"message\":\"\"}"))
							.then();
				}))
				.bindNow();
	}

	@AfterAll
	static void stopUserService() {
		USER_SERVICE.disposeNow();
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("cherry.gateway.user-service-base-url", () ->
				"http://127.0.0.1:" + USER_SERVICE.port());
	}

	@Test
	void csrfLoginRotatesSessionAndLogoutDeletesItFromRedis() {
		WebTestClient client = WebTestClient.bindToServer()
				.baseUrl("http://127.0.0.1:" + port).build();

		EntityExchangeResult<byte[]> csrf = client.get().uri("/api/auth/csrf")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
				.expectBody().returnResult();
		ResponseCookie anonymousCookie = csrf.getResponseCookies().getFirst("CHERRY_SESSION");
		assertCookiePolicy(anonymousCookie);
		String csrfToken = token(csrf);

		EntityExchangeResult<byte[]> login = client.post().uri("/api/auth/login")
				.cookie("CHERRY_SESSION", anonymousCookie.getValue())
				.header("X-CSRF-Token", csrfToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"username\":\"admin01\",\"password\":\"correct-password\"}")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
				.expectBody()
				.jsonPath("$.data.authenticated").isEqualTo(true)
				.jsonPath("$.data.user.role").isEqualTo("ADMIN")
				.returnResult();
		ResponseCookie authenticatedCookie = login.getResponseCookies().getFirst("CHERRY_SESSION");
		assertCookiePolicy(authenticatedCookie);
		assertThat(authenticatedCookie.getValue()).isNotEqualTo(anonymousCookie.getValue());
		assertThat(new String(login.getResponseBody(), StandardCharsets.UTF_8))
				.doesNotContain("login-grant-canary")
				.doesNotContain("internal-jwt-canary");

		client.get().uri("/api/auth/session")
				.cookie("CHERRY_SESSION", authenticatedCookie.getValue())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.data.authenticated").isEqualTo(true)
				.jsonPath("$.data.user.username").isEqualTo("admin01")
				.jsonPath("$.data.accessToken").doesNotExist()
				.jsonPath("$.data.loginGrant").doesNotExist();

		assertThat(redis.keys("cherry:gateway:sessions:*").collectList().block()).isNotEmpty();
		client.post().uri("/api/auth/logout")
				.cookie("CHERRY_SESSION", authenticatedCookie.getValue())
				.header("X-CSRF-Token", csrfToken)
				.exchange()
				.expectStatus().isNoContent();
		assertThat(redis.keys("cherry:gateway:sessions:*").collectList().block()).isEmpty();
	}

	@Test
	void loginWithoutCsrfReturnsRedactedProblemBeforePasswordHandling() {
		WebTestClient client = WebTestClient.bindToServer()
				.baseUrl("http://127.0.0.1:" + port).build();
		int callsBefore = AUTHENTICATE_CALLS.get();

		client.post().uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"username\":\"admin01\",\"password\":\"password-canary\"}")
				.exchange()
				.expectStatus().isForbidden()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.code").isEqualTo("CSRF_REJECTED")
				.jsonPath("$.detail").isEqualTo("请求缺少有效的 CSRF token。");
		assertThat(AUTHENTICATE_CALLS.get()).isEqualTo(callsBefore);
	}

	@Test
	void rejectedDelegatedTokenIsExchangedOnceAndAdminListRecoversWithoutLogout() {
		WebTestClient client = WebTestClient.bindToServer()
				.baseUrl("http://127.0.0.1:" + port).build();
		int exchangesBefore = TOKEN_EXCHANGE_CALLS.get();
		int adminCallsBefore = ADMIN_USER_CALLS.get();
		EntityExchangeResult<byte[]> csrf = client.get().uri("/api/auth/csrf")
				.exchange().expectStatus().isOk().expectBody().returnResult();
		ResponseCookie anonymousCookie = csrf.getResponseCookies().getFirst("CHERRY_SESSION");
		EntityExchangeResult<byte[]> login = client.post().uri("/api/auth/login")
				.cookie("CHERRY_SESSION", anonymousCookie.getValue())
				.header("X-CSRF-Token", token(csrf))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"username\":\"admin01\",\"password\":\"correct-password\"}")
				.exchange().expectStatus().isOk().expectBody().returnResult();
		ResponseCookie authenticatedCookie = login.getResponseCookies().getFirst("CHERRY_SESSION");

		client.get().uri("/api/admin/users?page=1&size=20")
				.cookie("CHERRY_SESSION", authenticatedCookie.getValue())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.data.items[0].username").isEqualTo("admin01")
				.jsonPath("$.meta.pagination.totalElements").isEqualTo(1);

		assertThat(TOKEN_EXCHANGE_CALLS.get() - exchangesBefore).isEqualTo(1);
		assertThat(ADMIN_USER_CALLS.get() - adminCallsBefore).isEqualTo(2);
		client.get().uri("/api/auth/session")
				.cookie("CHERRY_SESSION", authenticatedCookie.getValue())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.data.authenticated").isEqualTo(true);
	}

	@Test
	void redisSessionRepositoryUsesConfiguredIdleSeconds() {
		Session session = sessions.createSession().cast(Session.class).block();
		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(java.time.Duration.ofSeconds(1_800));
	}

	private static String token(EntityExchangeResult<byte[]> response) {
		String body = new String(response.getResponseBody(), StandardCharsets.UTF_8);
		Matcher matcher = TOKEN_PATTERN.matcher(body);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private static void assertCookiePolicy(ResponseCookie cookie) {
		assertThat(cookie).isNotNull();
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.isSecure()).isFalse();
		assertThat(cookie.getSameSite()).isEqualToIgnoringCase("Lax");
		assertThat(cookie.getPath()).isEqualTo("/api");
		assertThat(cookie.getDomain()).isNull();
	}

	private static String authenticationJson() {
		Instant started = Instant.now();
		LocalDateTime idleExpiresAt = LocalDateTime.ofInstant(started.plusSeconds(1_800), ZoneOffset.UTC);
		sessionAbsoluteExpiresAt = LocalDateTime.ofInstant(started.plusSeconds(43_200), ZoneOffset.UTC);
		return """
				{
				  "user": {
				    "id": "019c8e42-7f70-7000-8000-000000000001",
				    "username": "admin01",
				    "role": "ADMIN",
				    "status": "ACTIVE",
				    "passwordChangeRequired": false,
				    "createdAt": "2026-08-26T01:00:00",
				    "updatedAt": "2026-08-26T01:00:00",
				    "rowVersion": 0
				  },
				  "loginGrant": "login-grant-canary",
				  "accessToken": "internal-jwt-canary",
				  "accessTokenExpiresAt": "%s",
				  "sessionIdleExpiresAt": "%s",
				  "sessionAbsoluteExpiresAt": "%s",
				  "sessionIdleTimeoutSeconds": 1800,
				  "sessionAbsoluteTimeoutSeconds": 43200,
				  "sessionRefreshIdleOnActivity": true
				}
				""".formatted(
					started.plusSeconds(120), idleExpiresAt, sessionAbsoluteExpiresAt);
	}

	private static String touchJson() {
		LocalDateTime idleExpiresAt = LocalDateTime.ofInstant(
				Instant.now().plusSeconds(1_800), ZoneOffset.UTC);
		return """
				{
				  "sessionIdleExpiresAt": "%s",
				  "sessionAbsoluteExpiresAt": "%s",
				  "sessionIdleTimeoutSeconds": 1800,
				  "sessionAbsoluteTimeoutSeconds": 43200,
				  "sessionRefreshIdleOnActivity": true
				}
				""".formatted(idleExpiresAt, sessionAbsoluteExpiresAt);
	}

	private static String tokenExchangeJson() {
		Instant exchanged = Instant.now();
		LocalDateTime idleExpiresAt = LocalDateTime.ofInstant(
				exchanged.plusSeconds(1_800), ZoneOffset.UTC);
		return """
				{
				  "user": {
				    "id": "019c8e42-7f70-7000-8000-000000000001",
				    "username": "admin01",
				    "role": "ADMIN",
				    "status": "ACTIVE",
				    "passwordChangeRequired": false,
				    "createdAt": "2026-08-26T01:00:00",
				    "updatedAt": "2026-08-26T01:00:00",
				    "rowVersion": 0
				  },
				  "accessToken": "fresh-internal-jwt-canary",
				  "accessTokenExpiresAt": "%s",
				  "sessionIdleExpiresAt": "%s",
				  "sessionAbsoluteExpiresAt": "%s",
				  "sessionIdleTimeoutSeconds": 1800,
				  "sessionAbsoluteTimeoutSeconds": 43200,
				  "sessionRefreshIdleOnActivity": true
				}
				""".formatted(
					exchanged.plusSeconds(120), idleExpiresAt, sessionAbsoluteExpiresAt);
	}

	private static String userPageJson() {
		return """
				{
				  "items": [{
				    "id": "019c8e42-7f70-7000-8000-000000000001",
				    "username": "admin01",
				    "role": "ADMIN",
				    "status": "ACTIVE",
				    "passwordChangeRequired": false,
				    "createdAt": "2026-08-26T01:00:00",
				    "updatedAt": "2026-08-26T01:00:00",
				    "rowVersion": 0
				  }],
				  "page": 1,
				  "size": 20,
				  "totalElements": 1,
				  "totalPages": 1
				}
				""";
	}
}
