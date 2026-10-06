package com.cherryoj.problemservice.integration.judging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.config.JudgingClientProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HttpJudgingClientTests {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void delegatesJwtAndTraceAndSendsTheTestDataAddressAndDigestInTheBody() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> traceparent = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<byte[]> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/admin/calibrations", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, """
                    {"id":"019c8e42-7f70-7000-8000-000000000011","problemId":"019c8e42-7f70-7000-8000-000000000010",
                     "languageId":"cpp","status":"VALID","cpuNs":1000000000,"memoryBytes":268435456,"clockNs":null,
                     "testDataDigest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                     "benchmarkSummary":null,"errorMessage":null,"createdAt":null,"updatedAt":null,"rowVersion":1}
                    """);
        });
        server.start();
        HttpJudgingClient client = client(Duration.ofSeconds(5));
        var request = new JudgingDtos.CalibrationRequest(
                "019c8e42-7f70-7000-8000-000000000010", "cpp", "/srv/problem/p", "a".repeat(64),
                1_000_000_000L, 268_435_456L, null, "int main() { return 0; }");

        var response = client.calibrate(request, "secret-delegated-jwt",
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");

        assertThat(response.status()).isEqualTo("VALID");
        assertThat(response.testDataDigest()).isEqualTo("a".repeat(64));
        assertThat(authorization.get()).isEqualTo("Bearer secret-delegated-jwt");
        assertThat(traceparent.get()).startsWith("00-");
        assertThat(contentType.get()).isEqualTo("application/json");
        assertThat(new String(requestBody.get(), StandardCharsets.UTF_8))
                .contains("\"testDataLocation\":\"/srv/problem/p\"", "\"testDataDigest\":\"" + "a".repeat(64) + "\"")
                .doesNotContain("secret-delegated-jwt", "ersion");
    }

    @Test
    void readinessCarriesTheProblemLanguageAndDigestAsQueryParameters() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/admin/readiness", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "{\"ready\":false,\"checks\":[],\"executionProfile\":null}");
        });
        server.start();

        var readiness = client(Duration.ofSeconds(2)).readiness(
                "019c8e42-7f70-7000-8000-000000000010", "cpp", "b".repeat(64), "jwt", null);

        assertThat(readiness.ready()).isFalse();
        assertThat(query.get()).isEqualTo("problemId=019c8e42-7f70-7000-8000-000000000010&languageId=cpp&testDataDigest="
                + "b".repeat(64));
    }

    @Test
    void mapsDownstreamFailuresToStableSafeErrors() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/admin/readiness", exchange -> respond(exchange, 503, "private downstream detail"));
        server.start();

        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).readiness(
                "019c8e42-7f70-7000-8000-000000000001", "cpp", "a".repeat(64), "jwt", null))
                .isInstanceOfSatisfying(ProblemApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("JUDGING_UNAVAILABLE");
                    assertThat(error.getMessage()).doesNotContain("private downstream detail", "jwt");
                });
    }

    @Test
    void nodeErrorsPreserveOnlyAllowlistedCodesAndFixedDetails() throws Exception {
        var code = new AtomicReference<>("NO_ONLINE_JUDGE_NODE");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/admin/readiness", exchange -> respond(exchange, 503,
                "{\"code\":\"" + code.get() + "\",\"detail\":\"private token path\"}"));
        server.start();
        for (String value : List.of("NO_ONLINE_JUDGE_NODE", "JUDGE_NODE_UNREACHABLE", "UNKNOWN")) {
            code.set(value);
            assertThatThrownBy(() -> client(Duration.ofSeconds(2)).readiness("problem", "cpp", "hash", "jwt", null))
                    .isInstanceOfSatisfying(ProblemApiException.class, error -> {
                        assertThat(error.code()).isEqualTo(value.equals("UNKNOWN") ? "JUDGING_UNAVAILABLE" : value);
                        assertThat(error.getMessage()).doesNotContain("private", "token", "path");
                    });
        }
    }

    private HttpJudgingClient client(Duration timeout) {
        return new HttpJudgingClient(new JudgingClientProperties(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofSeconds(1), timeout),
                JsonMapper.builder().build());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
