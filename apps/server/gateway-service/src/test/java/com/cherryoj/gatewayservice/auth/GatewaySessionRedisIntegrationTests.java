package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.cherryoj.gatewayservice.TestCalls;
import com.cherryoj.identity.token.CallUser;
import com.cherryoj.identity.token.ServiceNames;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.session.Session;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class GatewaySessionRedisIntegrationTests {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");
    private static final AtomicInteger AUTHENTICATE_CALLS = new AtomicInteger();
    private static final AtomicInteger ADMIN_USER_CALLS = new AtomicInteger();
    private static final AtomicInteger TEST_DATA_UPLOAD_CALLS = new AtomicInteger();
    private static final AtomicReference<String> TEST_DATA_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> AUTH_AUTHORIZATION = new AtomicReference<>();
    private static final String SESSION_ID = "session-id-canary-" + "x".repeat(32);
    private static volatile Instant sessionExpiresAt;
    private static final DisposableServer USER_SERVICE = startUserService();

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @LocalServerPort
    private int port;

    private final ReactiveStringRedisTemplate redis;
    private final ReactiveRedisSessionRepository sessions;

    @Autowired
    GatewaySessionRedisIntegrationTests(ReactiveStringRedisTemplate redis, ReactiveRedisSessionRepository sessions) {
        this.redis = redis;
        this.sessions = sessions;
    }

    private static DisposableServer startUserService() {
        return HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.post("/internal/auth/authenticate", (request, response) -> {
                            AUTHENTICATE_CALLS.incrementAndGet();
                            AUTH_AUTHORIZATION.set(request.requestHeaders().get(HttpHeaders.AUTHORIZATION));
                            return request.receive()
                                    .aggregate()
                                    .then(response.status(HttpStatus.OK.value())
                                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                            .sendString(Mono.just(authenticationJson()))
                                            .then());
                        })
                        .post(
                                "/internal/auth/validate",
                                (request, response) -> request.receive()
                                        .aggregate()
                                        .then(response.status(HttpStatus.OK.value())
                                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                                .sendString(Mono.just(validateJson()))
                                                .then()))
                        .post(
                                "/internal/auth/revoke",
                                (request, response) -> request.receive()
                                        .aggregate()
                                        .then(response.status(HttpStatus.NO_CONTENT.value())
                                                .send()))
                        .get("/internal/admin/users", (request, response) -> {
                            ADMIN_USER_CALLS.incrementAndGet();
                            // 模拟 user-service 拒绝了 gateway 的调用令牌（例如公钥目录没有更新）。
                            return response.status(HttpStatus.UNAUTHORIZED.value())
                                    .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                                    .sendString(Mono.just("{\"code\":\"CALL_UNAUTHENTICATED\",\"status\":401}"))
                                    .then();
                        })
                        .put("/internal/admin/problems/{problemId}/test-data", (request, response) -> {
                            TEST_DATA_UPLOAD_CALLS.incrementAndGet();
                            TEST_DATA_AUTHORIZATION.set(request.requestHeaders().get(HttpHeaders.AUTHORIZATION));
                            return request.receive()
                                    .aggregate()
                                    .then(response.status(HttpStatus.OK.value())
                                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                            .sendString(Mono.just(testDataJson()))
                                            .then());
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
        registry.add("cherry.gateway.user-service-base-url", () -> "http://127.0.0.1:" + USER_SERVICE.port());
        registry.add("cherry.gateway.problem-service.base-url", () -> "http://127.0.0.1:" + USER_SERVICE.port());
    }

    @Test
    void csrfLoginRotatesSessionAndLogoutDeletesItFromRedis() {
        WebTestClient client =
                WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();

        EntityExchangeResult<byte[]> csrf = client.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .returnResult();
        ResponseCookie anonymousCookie = csrf.getResponseCookies().getFirst("CHERRY_SESSION");
        assertCookiePolicy(anonymousCookie);
        String csrfToken = token(csrf);

        EntityExchangeResult<byte[]> login = client.post()
                .uri("/api/auth/login")
                .cookie("CHERRY_SESSION", anonymousCookie.getValue())
                .header("X-CSRF-Token", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin01\",\"password\":\"correct-password\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.data.authenticated")
                .isEqualTo(true)
                .jsonPath("$.data.user.role")
                .isEqualTo("ADMIN")
                .returnResult();
        ResponseCookie authenticatedCookie = login.getResponseCookies().getFirst("CHERRY_SESSION");
        assertCookiePolicy(authenticatedCookie);
        assertThat(authenticatedCookie.getValue()).isNotEqualTo(anonymousCookie.getValue());
        assertThat(new String(login.getResponseBody(), StandardCharsets.UTF_8)).doesNotContain(SESSION_ID);

        client.get()
                .uri("/api/auth/session")
                .cookie("CHERRY_SESSION", authenticatedCookie.getValue())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.authenticated")
                .isEqualTo(true)
                .jsonPath("$.data.user.username")
                .isEqualTo("admin01")
                .jsonPath("$.data.sessionId")
                .doesNotExist();

        assertThat(redis.keys("cherry:gateway:sessions:*").collectList().block())
                .isNotEmpty();
        client.post()
                .uri("/api/auth/logout")
                .cookie("CHERRY_SESSION", authenticatedCookie.getValue())
                .header("X-CSRF-Token", csrfToken)
                .exchange()
                .expectStatus()
                .isNoContent();
        assertThat(redis.keys("cherry:gateway:sessions:*").collectList().block())
                .isEmpty();
    }

    @Test
    void loginWithoutCsrfReturnsRedactedProblemBeforePasswordHandling() {
        WebTestClient client =
                WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        int callsBefore = AUTHENTICATE_CALLS.get();

        client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin01\",\"password\":\"password-canary\"}")
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("CSRF_REJECTED")
                .jsonPath("$.detail")
                .isEqualTo("请求缺少有效的 CSRF token。");
        assertThat(AUTHENTICATE_CALLS.get()).isEqualTo(callsBefore);
        // 登录调用带的是 gateway 自己签发、发给 user-service、不代表用户的调用令牌。
        var call = TestCalls.verify(AUTH_AUTHORIZATION.get(), ServiceNames.USER);
        assertThat(call.caller()).isEqualTo(ServiceNames.GATEWAY);
        assertThat(call.user()).isEmpty();
    }

    @Test
    void aRejectedCallTokenBecomes503IsNotReplayedAndKeepsTheSession() {
        WebTestClient client =
                WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        int adminCallsBefore = ADMIN_USER_CALLS.get();
        EntityExchangeResult<byte[]> csrf = client.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult();
        ResponseCookie anonymousCookie = csrf.getResponseCookies().getFirst("CHERRY_SESSION");
        EntityExchangeResult<byte[]> login = client.post()
                .uri("/api/auth/login")
                .cookie("CHERRY_SESSION", anonymousCookie.getValue())
                .header("X-CSRF-Token", token(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin01\",\"password\":\"correct-password\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult();
        ResponseCookie authenticatedCookie = login.getResponseCookies().getFirst("CHERRY_SESSION");

        client.get()
                .uri("/api/admin/users?page=1&size=20")
                .cookie("CHERRY_SESSION", authenticatedCookie.getValue())
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("SERVICE_UNAVAILABLE");

        assertThat(ADMIN_USER_CALLS.get() - adminCallsBefore).isEqualTo(1);
        client.get()
                .uri("/api/auth/session")
                .cookie("CHERRY_SESSION", authenticatedCookie.getValue())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.authenticated")
                .isEqualTo(true);
    }

    @Test
    void redisSessionRepositoryUsesFixedAbsoluteLifetimeAsItsOnlyTtl() {
        Session session = sessions.createSession().cast(Session.class).block();
        assertThat(session).isNotNull();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(java.time.Duration.ofDays(30));
    }

    @Test
    void validZipStreamsThroughGatewayExactlyOnce() throws IOException {
        WebTestClient client =
                WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        int uploadsBefore = TEST_DATA_UPLOAD_CALLS.get();
        EntityExchangeResult<byte[]> csrf = client.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult();
        ResponseCookie anonymousCookie = csrf.getResponseCookies().getFirst("CHERRY_SESSION");
        EntityExchangeResult<byte[]> login = client.post()
                .uri("/api/auth/login")
                .cookie("CHERRY_SESSION", anonymousCookie.getValue())
                .header("X-CSRF-Token", token(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin01\",\"password\":\"correct-password\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult();
        ResponseCookie authenticatedCookie = login.getResponseCookies().getFirst("CHERRY_SESSION");
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart
                .part("file", new ByteArrayResource(zipBytes()) {
                    @Override
                    public String getFilename() {
                        return "test-data.zip";
                    }
                })
                .contentType(MediaType.parseMediaType("application/zip"));

        client.put()
                .uri("/api/admin/problems/019c8e42-7f70-7000-8000-000000000101/test-data")
                .cookie("CHERRY_SESSION", authenticatedCookie.getValue())
                .header("X-CSRF-Token", token(csrf))
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.digest")
                .isEqualTo("6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4");

        assertThat(TEST_DATA_UPLOAD_CALLS.get() - uploadsBefore).isEqualTo(1);
        var call = TestCalls.verify(TEST_DATA_AUTHORIZATION.get(), ServiceNames.PROBLEM);
        assertThat(call.user()).contains(new CallUser("019c8e42-7f70-7000-8000-000000000001", "ADMIN"));
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
        assertThat(cookie.getMaxAge()).isNegative();
    }

    private static String authenticationJson() {
        sessionExpiresAt = Instant.now().plusSeconds(2_592_000);
        return """
				{
				  "sessionId": "%s",
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
				  "sessionExpiresAt": "%s"
				}
				""".formatted(SESSION_ID, sessionExpiresAt);
    }

    private static String validateJson() {
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
				  "sessionExpiresAt": "%s"
				}
				""".formatted(sessionExpiresAt);
    }

    private static byte[] zipBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("1.in"));
            zip.write("1 2\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("1.out"));
            zip.write("3\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static String testDataJson() {
        return """
				{"digest":"6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4",
				 "testcaseCount":2,"totalBytes":16,"updatedAt":"2026-09-05T00:00:00",
				 "manifest":{"testcaseCount":2,"totalBytes":16,"files":[]}}
				""";
    }
}
