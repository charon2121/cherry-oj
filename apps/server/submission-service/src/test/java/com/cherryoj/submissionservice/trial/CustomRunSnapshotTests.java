package com.cherryoj.submissionservice.trial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cherryoj.submissionservice.api.*;
import com.cherryoj.submissionservice.integration.SubmissionPrerequisites;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

class CustomRunSnapshotTests {

    @Test
    void snapshotOfAnotherProblemIsNeverForwarded() {
        var prerequisite = mock(SubmissionPrerequisites.class);
        var id = UUID.randomUUID();
        when(prerequisite.snapshot(id.toString(), "cpp"))
                .thenReturn(
                        new SubmissionDtos.Snapshot(UUID.randomUUID().toString(), "title", "cpp", "ACM", null, null));
        var controller = new CustomRunController(
                prerequisite,
                new ObjectMapper(),
                "http://127.0.0.1:1",
                mock(com.cherryoj.serviceidentity.ServiceTokenClient.class));
        var jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .build();
        var request = new CustomRunController.Request(id, "cpp", "int main(){}", "");
        var error = assertThrows(
                SubmissionException.class, () -> controller.run(request, new JwtAuthenticationToken(jwt), null, null));
        assertEquals("CUSTOM_RUN_UNAVAILABLE", error.code());
        verify(prerequisite, never()).profile(any());
    }

    @Test
    void forwardsFrozenInputWithoutAnyDownstreamFeatureFlag() throws Exception {
        var prerequisite = mock(SubmissionPrerequisites.class);
        var id = UUID.randomUUID();
        var json = new ObjectMapper();
        when(prerequisite.snapshot(id.toString(), "cpp"))
                .thenReturn(new SubmissionDtos.Snapshot(id.toString(), "title", "cpp", "ACM", null, null));
        var captured = new java.util.concurrent.atomic.AtomicReference<tools.jackson.databind.JsonNode>();
        var authorization = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/submission/trials", exchange -> {
            captured.set(json.readTree(exchange.getRequestBody().readAllBytes()));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response =
                    "{\"status\":\"COMPLETED\",\"cpuNs\":0,\"memoryBytes\":0,\"stdout\":{\"text\":\"\",\"capturedBytes\":0,\"truncated\":false},\"stderr\":{\"text\":\"\",\"capturedBytes\":0,\"truncated\":false},\"effectiveLimits\":{\"cpuNs\":1,\"clockNs\":1,\"memoryBytes\":1}}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
        try {
            var token = "submission-service-token-canary";
            var tokens = mock(com.cherryoj.serviceidentity.ServiceTokenClient.class);
            when(tokens.tokenFor("judging-service")).thenReturn(token);
            var controller = new CustomRunController(
                    prerequisite,
                    json,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    tokens);
            var jwt = Jwt.withTokenValue("test")
                    .header("alg", "none")
                    .subject(UUID.randomUUID().toString())
                    .build();
            var response = controller.run(
                    new CustomRunController.Request(id, "cpp", "int main(){}", ""),
                    new JwtAuthenticationToken(jwt),
                    null,
                    null);
            assertEquals("COMPLETED", response.getBody().path("status").asText());
            assertEquals(id.toString(), response.getBody().path("problemId").asText());
            assertFalse(response.getBody().has("problemVersionId"));
            assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
            assertEquals("Bearer " + token, authorization.get());
            assertEquals(id.toString(), captured.get().path("problemId").asText());
            assertFalse(captured.get().has("testDataVersionId"));
            assertFalse(captured.get().has("problemVersionId"));
            assertEquals("", captured.get().path("inputText").asText());
            assertEquals("int main(){}", captured.get().path("source").asText());
            assertFalse(captured.get().has("solutionSource"));
            verify(prerequisite, never()).profile(any());
        } finally {
            server.stop(0);
        }
    }

    /** CORE 自测与正式提交同一套合并：source 是驱动文件，solutionSource 是前缀 + 用户代码，并带上前缀行数。 */
    @Test
    void coreRunSendsTheDriverAndThePrefixedSolutionFile() throws Exception {
        var prerequisite = mock(SubmissionPrerequisites.class);
        var id = UUID.randomUUID();
        var json = new ObjectMapper();
        when(prerequisite.snapshot(id.toString(), "cpp"))
                .thenReturn(new SubmissionDtos.Snapshot(
                        id.toString(),
                        "title",
                        "cpp",
                        "CORE",
                        "#include \"solution.cpp\"\nint main(){}\n",
                        "#include <bits/stdc++.h>\nusing namespace std;\n"));
        var captured = new java.util.concurrent.atomic.AtomicReference<tools.jackson.databind.JsonNode>();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/submission/trials", exchange -> {
            captured.set(json.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] response =
                    "{\"status\":\"COMPLETED\",\"cpuNs\":0,\"memoryBytes\":0,\"stdout\":{\"text\":\"\",\"capturedBytes\":0,\"truncated\":false},\"stderr\":{\"text\":\"\",\"capturedBytes\":0,\"truncated\":false},\"effectiveLimits\":{\"cpuNs\":1,\"clockNs\":1,\"memoryBytes\":1}}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
        try {
            var tokens = mock(com.cherryoj.serviceidentity.ServiceTokenClient.class);
            when(tokens.tokenFor("judging-service")).thenReturn("token");
            var controller = new CustomRunController(
                    prerequisite,
                    json,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    tokens);
            var jwt = Jwt.withTokenValue("test")
                    .header("alg", "none")
                    .subject(UUID.randomUUID().toString())
                    .build();
            controller.run(
                    new CustomRunController.Request(id, "cpp", "class Solution {};\n", "1 2\n"),
                    new JwtAuthenticationToken(jwt),
                    null,
                    null);
            assertEquals(
                    "#include \"solution.cpp\"\nint main(){}\n",
                    captured.get().path("source").asText());
            assertEquals(
                    "#include <bits/stdc++.h>\nusing namespace std;\nclass Solution {};\n",
                    captured.get().path("solutionSource").asText());
            assertEquals(2, captured.get().path("solutionPrefixLineCount").asInt());
        } finally {
            server.stop(0);
        }
    }
}
