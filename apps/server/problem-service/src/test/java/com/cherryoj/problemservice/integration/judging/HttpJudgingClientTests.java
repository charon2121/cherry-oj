package com.cherryoj.problemservice.integration.judging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.config.JudgingClientProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
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
    void delegatesJwtAndTraceAndStreamsMultipartWithoutExposingThemInUrls() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> traceparent = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<byte[]> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/admin/deployments", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, """
                    {"testDataVersionId":"019c8e42-7f70-7000-8000-000000000010",
                     "environmentId":"019c8e42-7f70-7000-8000-000000000020","environmentName":"linux",
                     "expectedSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                     "status":"READY","deployedSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                     "deployedAt":null,"errorMessage":null,"updatedAt":null,"rowVersion":1}
                    """);
        });
        server.start();
        HttpJudgingClient client = client(Duration.ofSeconds(5));
        byte[] archive = "zip-stream-canary".getBytes(StandardCharsets.UTF_8);
        var metadata = new JudgingDtos.DeploymentMetadata(
                "019c8e42-7f70-7000-8000-000000000010", "a".repeat(64),
                new JudgingDtos.Manifest(1, archive.length, List.of(
                        new JudgingDtos.ManifestFile("1.in", archive.length, "b".repeat(64)),
                        new JudgingDtos.ManifestFile("1.out", 0, "c".repeat(64)))));

        var response = client.deploy(metadata, new ByteArrayInputStream(archive), "secret-delegated-jwt",
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");

        assertThat(response.status()).isEqualTo("READY");
        assertThat(authorization.get()).isEqualTo("Bearer secret-delegated-jwt");
        assertThat(traceparent.get()).startsWith("00-");
        assertThat(contentType.get()).startsWith("multipart/form-data; boundary=cherry-");
        assertThat(new String(requestBody.get(), StandardCharsets.UTF_8))
                .contains("name=\"metadata\"", "name=\"archive\"", metadata.testDataVersionId(), "zip-stream-canary")
                .doesNotContain("secret-delegated-jwt");
    }

    @Test
    void mapsDownstreamFailuresToStableSafeErrors() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/admin/readiness", exchange -> respond(exchange, 503, "private downstream detail"));
        server.start();

        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).readiness(
                "019c8e42-7f70-7000-8000-000000000001",
                "019c8e42-7f70-7000-8000-000000000002", "a".repeat(64), "cpp", "jwt", null))
                .isInstanceOfSatisfying(ProblemApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("JUDGING_UNAVAILABLE");
                    assertThat(error.getMessage()).doesNotContain("private downstream detail", "jwt");
                });
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
