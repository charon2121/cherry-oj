package com.cherryoj.submissionservice.trial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cherryoj.identity.token.CallToken;
import com.cherryoj.identity.token.CallTokenSigner;
import com.cherryoj.identity.token.PublicKeyDirectory;
import com.cherryoj.identity.token.ServiceKeys;
import com.cherryoj.identity.token.ServiceNames;
import com.cherryoj.submissionservice.api.*;
import com.cherryoj.submissionservice.integration.SubmissionPrerequisites;
import com.cherryoj.submissionservice.judging.ProblemJudgeSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class CustomRunSnapshotTests {
    private static final CallTokenSigner SIGNER = new CallTokenSigner(
            ServiceNames.SUBMISSION,
            ServiceKeys.privateKey("classpath:test-service-identity/submission-service-private.pem"),
            Clock.systemUTC());

    @Test
    void customRunBodyLimitAcceptsTwoMebibytesAndRejectsOneMoreByte() throws Exception {
        var prerequisites = mock(SubmissionPrerequisites.class);
        var controller = new CustomRunController(prerequisites, new ObjectMapper(), "http://127.0.0.1:1", SIGNER);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new SubmissionExceptionHandler(), new RunBodyLimit())
                .build();
        String id = "019c8e42-7f70-7000-8000-000000000010";
        String body = """
                {"problemId":"%s","languageId":"cpp","source":"int main(){}","inputText":""}
                """.formatted(id);
        String atLimit = body + " ".repeat(2 * 1024 * 1024 - body.length());

        mvc.perform(post("/api/custom-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atLimit + " "))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("RUN_INPUT_TOO_LARGE"));
        verifyNoInteractions(prerequisites);
        mvc.perform(post("/api/custom-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atLimit))
                .andExpect(status().isServiceUnavailable());
        verify(prerequisites).resolveProblemJudgeSnapshot(id, "cpp");
    }

    @Test
    void snapshotOfAnotherProblemIsNeverForwarded() {
        var prerequisite = mock(SubmissionPrerequisites.class);
        var id = UUID.randomUUID();
        when(prerequisite.resolveProblemJudgeSnapshot(id.toString(), "cpp"))
                .thenReturn(new ProblemJudgeSnapshot(UUID.randomUUID().toString(), "title", "cpp", "ACM", null, null));
        var controller = new CustomRunController(prerequisite, new ObjectMapper(), "http://127.0.0.1:1", SIGNER);
        var request = new CustomRunController.Request(id, "cpp", "int main(){}", "");
        var error = assertThrows(SubmissionException.class, () -> controller.run(request, null, null));
        assertEquals("CUSTOM_RUN_UNAVAILABLE", error.code());
        verify(prerequisite, never()).resolveExecutionProfile(any());
    }

    @Test
    void forwardsFrozenInputWithoutAnyDownstreamFeatureFlag() throws Exception {
        var prerequisite = mock(SubmissionPrerequisites.class);
        var id = UUID.randomUUID();
        var json = new ObjectMapper();
        when(prerequisite.resolveProblemJudgeSnapshot(id.toString(), "cpp"))
                .thenReturn(new ProblemJudgeSnapshot(id.toString(), "title", "cpp", "ACM", null, null));
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
            var controller = new CustomRunController(
                    prerequisite,
                    json,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    SIGNER);
            long before = System.currentTimeMillis();
            var response = controller.run(new CustomRunController.Request(id, "cpp", "int main(){}", ""), null, null);
            long deadline = captured.get().path("deadlineEpochMs").asLong();
            assertTrue(deadline >= before + 194000 && deadline <= System.currentTimeMillis() + 195000);
            long outer = System.currentTimeMillis() + 60000;
            controller.run(new CustomRunController.Request(id, "cpp", "int main(){}", ""), outer, null);
            assertEquals(outer, captured.get().path("deadlineEpochMs").asLong());
            assertEquals("COMPLETED", response.getBody().path("status").asText());
            assertEquals(id.toString(), response.getBody().path("problemId").asText());
            assertFalse(response.getBody().has("problemVersionId"));
            assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
            // 自测不代表用户调用 judging-service：令牌由本服务签发，aud 是 judging-service，不带 user
            var call = CallToken.verify(
                    authorization.get().substring("Bearer ".length()),
                    PublicKeyDirectory.load("classpath:test-service-identity"),
                    ServiceNames.JUDGING,
                    Instant.now());
            assertEquals(ServiceNames.SUBMISSION, call.caller());
            assertTrue(call.user().isEmpty());
            assertEquals(id.toString(), captured.get().path("problemId").asText());
            assertFalse(captured.get().has("testDataVersionId"));
            assertFalse(captured.get().has("problemVersionId"));
            assertEquals("", captured.get().path("inputText").asText());
            assertEquals("int main(){}", captured.get().path("source").asText());
            assertFalse(captured.get().has("solutionSource"));
            verify(prerequisite, never()).resolveExecutionProfile(any());
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
        when(prerequisite.resolveProblemJudgeSnapshot(id.toString(), "cpp"))
                .thenReturn(new ProblemJudgeSnapshot(
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
            var controller = new CustomRunController(
                    prerequisite,
                    json,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    SIGNER);
            controller.run(new CustomRunController.Request(id, "cpp", "class Solution {};\n", "1 2\n"), null, null);
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
