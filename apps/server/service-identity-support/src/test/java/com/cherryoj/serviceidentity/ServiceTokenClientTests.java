package com.cherryoj.serviceidentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServiceTokenClientTests {

    private static final String ISSUER = "cherry-oj-user-service";
    private static final java.security.KeyPair KEYS = TestKeys.rsa(2048);
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> lastAssertion = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void signsAnAssertionCachesPerAudienceAndRefreshesBeforeExpiry() throws Exception {
        var now = new AtomicReference<>(Instant.parse("2026-10-16T08:00:00Z"));
        var client = client(now);

        String first = client.tokenFor("problem-service");
        String again = client.tokenFor("problem-service");
        client.tokenFor("submission-service");

        assertThat(first).isEqualTo(again);
        assertThat(requests.get()).isEqualTo(2);
        assertThat(lastBody.get()).contains("\"audience\":\"submission-service\"");
        var verified = ClientAssertion.verify(
                lastAssertion.get(),
                caller -> (RSAPublicKey) KEYS.getPublic(),
                ISSUER,
                now.get(),
                Duration.ofSeconds(30));
        assertThat(verified.caller()).isEqualTo("judging-service");

        // 剩余寿命不足 5 分钟时换新；之前一直复用。
        now.set(now.get().plus(Duration.ofMinutes(9)));
        client.tokenFor("problem-service");
        assertThat(requests.get()).isEqualTo(2);
        now.set(now.get().plus(Duration.ofMinutes(2)));
        client.tokenFor("problem-service");
        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void reportsRejectionWithStatusAndInvalidatesOnDemand() throws Exception {
        var now = new AtomicReference<>(Instant.parse("2026-10-16T08:00:00Z"));
        var client = client(now);
        client.tokenFor("problem-service");
        client.invalidate("problem-service");
        status.set(401);

        assertThatThrownBy(() -> client.tokenFor("problem-service"))
                .isInstanceOfSatisfying(ServiceIdentityException.class, error -> {
                    assertThat(error.reason()).isEqualTo("TOKEN_REQUEST_REJECTED");
                    assertThat(error.getMessage()).contains("status 401");
                });
    }

    private ServiceTokenClient client(AtomicReference<Instant> now) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/service-tokens", exchange -> {
            requests.incrementAndGet();
            lastAssertion.set(
                    exchange.getRequestHeaders().getFirst("Authorization").substring("Bearer ".length()));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"accessToken\":\"token-" + requests.get() + "\",\"expiresAt\":\""
                            + now.get().plus(ServiceTokenClaims.SERVICE_TOKEN_LIFETIME) + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        return new ServiceTokenClient(
                "judging-service",
                (RSAPrivateKey) KEYS.getPrivate(),
                ISSUER,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/internal/service-tokens"),
                clock);
    }
}
