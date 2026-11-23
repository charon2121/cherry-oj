package com.cherryoj.judgingservice.trial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cherryoj.judgingservice.api.JudgingApiException;
import com.cherryoj.judgingservice.application.ExecutionProfileService;
import com.cherryoj.judgingservice.application.ExecutionProfileService.Purpose;
import com.cherryoj.judgingservice.application.ExecutionProfileService.Reason;
import com.cherryoj.judgingservice.formal.FormalProperties;
import com.cherryoj.judgingservice.judge.LanguageCatalog;
import com.cherryoj.judgingservice.limits.ResolvedLanguageLimits;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CustomRunServiceTests {

    @Test
    void trialUsesOnlySuppliedInputAndKeepsIndependentStreams() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var payload = new AtomicReference<>(
                "{\"verdict\":\"RAN\",\"testcaseResults\":[{\"idx\":1,\"verdict\":\"RAN\",\"cpuNs\":0,\"memoryBytes\":0,\"output\":{\"excerpt\":\"\",\"bytes\":0},\"stderr\":{\"excerpt\":\"debug\",\"bytes\":5}}]}");
        var json = new ObjectMapper();
        server.createContext("/judge", exchange -> {
            var request = json.readTree(exchange.getRequestBody().readAllBytes());
            assertEquals("trial", request.path("mode").asText());
            // 自测不读测试数据：请求里没有地址，也没有任何版本字段
            assertFalse(request.has("testDataLocation"));
            assertFalse(request.has("problemVersionId"));
            assertFalse(request.has("testDataVersionId"));
            assertEquals(1, request.path("testcases").size());
            assertFalse(request.path("testcases").get(0).has("expected"));
            assertEquals("", request.path("testcases").get(0).path("input").asText());
            byte[] bytes = payload.get().getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        var profiles = mock(ExecutionProfileService.class);
        var nodes = mock(JudgeNodeRepository.class);
        var problem = UUID.randomUUID();
        when(profiles.resolve(eq(problem.toString()), anyString(), eq(Purpose.TRIAL), any()))
                .thenReturn(new ExecutionProfileService.Profile(
                        new ResolvedLanguageLimits(
                                "calibration", 1000000000, 67108864, 2000000000L, "BASE", null, null),
                        3000000000L));
        var node = mock(JudgeNodeRepository.Node.class);
        when(node.endpoint())
                .thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(nodes.online(eq("cpp"), any())).thenReturn(List.of(node));
        var service = new CustomRunService(
                profiles,
                nodes,
                mock(FormalProperties.class),
                json,
                HttpClient.newHttpClient(),
                new LanguageCatalog(json));
        var request = new CustomRunDtos.Request(
                problem, "cpp", "int main(){}", null, null, "", System.currentTimeMillis() + 45000);
        try {
            var result = service.run(request, null);
            assertEquals("COMPLETED", result.status());
            assertEquals("debug", result.stderr().text());
            assertEquals("", result.stdout().text());
            payload.set("{\"verdict\":\"RAN\"}");
            assertThrows(JudgingApiException.class, () -> service.run(request, null));
            payload.set("{\"verdict\":\"CE\",\"message\":\"/private/build/main.cpp: broken\"}");
            var ce = service.run(request, null);
            assertEquals("COMPILE_ERROR", ce.status());
            assertNull(ce.cpuNs());
            assertFalse(ce.compileDiagnostic().contains("/private"));
            var expired = new CustomRunDtos.Request(problem, "cpp", "int main(){}", null, null, "", 1);
            assertThrows(JudgingApiException.class, () -> service.run(expired, null));
        } finally {
            service.close();
            server.stop(0);
        }
    }

    /** CORE：用户文件随请求交给 judge；运行错误与编译诊断里的用户文件行号按前缀行数换算，标准输出原样返回。 */
    @Test
    void coreTrialSendsTheSolutionFileAndReportsEditorLineNumbers() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var payload = new AtomicReference<>("{\"verdict\":\"RE\",\"testcaseResults\":[{\"idx\":1,\"verdict\":\"RE\","
                + "\"output\":{\"excerpt\":\"solution.cpp:9\\n\",\"bytes\":15},"
                + "\"stderr\":{\"excerpt\":\"/w/solution.cpp:9:3: runtime error\\n\",\"bytes\":35}}]}");
        var json = new ObjectMapper();
        var received = new AtomicReference<tools.jackson.databind.JsonNode>();
        server.createContext("/judge", exchange -> {
            received.set(json.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] bytes = payload.get().getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        var profiles = mock(ExecutionProfileService.class);
        var nodes = mock(JudgeNodeRepository.class);
        var problem = UUID.randomUUID();
        when(profiles.resolve(eq(problem.toString()), anyString(), eq(Purpose.TRIAL), any()))
                .thenReturn(new ExecutionProfileService.Profile(
                        new ResolvedLanguageLimits(
                                "calibration", 1000000000, 67108864, 2000000000L, "BASE", null, null),
                        3000000000L));
        var node = mock(JudgeNodeRepository.Node.class);
        when(node.endpoint())
                .thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(nodes.online(eq("cpp"), any())).thenReturn(List.of(node));
        var service = new CustomRunService(
                profiles,
                nodes,
                mock(FormalProperties.class),
                json,
                HttpClient.newHttpClient(),
                new LanguageCatalog(json));
        var request = new CustomRunDtos.Request(
                problem,
                "cpp",
                "#include \"solution.cpp\"\nint main(){}",
                "#include <bits/stdc++.h>\nusing namespace std;\nclass Solution {};\n",
                2,
                "",
                System.currentTimeMillis() + 45000);
        try {
            var run = service.run(request, null);
            assertEquals(
                    "#include <bits/stdc++.h>\nusing namespace std;\nclass Solution {};\n",
                    received.get().path("solutionSource").asText());
            assertEquals("RUNTIME_ERROR", run.status());
            assertEquals("solution.cpp:7:3: runtime error\n", run.stderr().text());
            assertEquals("solution.cpp:9\n", run.stdout().text());
            payload.set("{\"verdict\":\"CE\",\"message\":\"solution.cpp:5:20: error: x\\nMain.cpp:8:1: note\\n\"}");
            var ce = service.run(request, null);
            assertEquals("solution.cpp:3:20: error: x\nMain.cpp:8:1: note\n", ce.compileDiagnostic());
            var missingCount = new CustomRunDtos.Request(
                    problem, "cpp", "int main(){}", "class Solution {};", null, "", System.currentTimeMillis() + 45000);
            assertThrows(JudgingApiException.class, () -> service.run(missingCount, null));
        } finally {
            service.close();
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void nodeWaitUsesTheExecutionBudgetAndTimeoutCancelsWorkWithoutLeakingTheSlot() {
        var profiles = mock(ExecutionProfileService.class);
        var nodes = mock(JudgeNodeRepository.class);
        var http = mock(HttpClient.class);
        var problem = UUID.randomUUID();
        var node = mock(JudgeNodeRepository.Node.class);
        when(node.endpoint()).thenReturn("http://unused.invalid");
        when(nodes.online(eq("c"), any())).thenReturn(List.of(node));
        var budgets = mock(FormalProperties.class);
        when(budgets.wallRatio()).thenReturn(10L);
        var request = new CustomRunDtos.Request(
                problem, "c", "int main(void){}", null, null, "", System.currentTimeMillis() + 180_000);
        var accepted = new ExecutionProfileService.Profile(
                new ResolvedLanguageLimits("cal", 2_000_000_000L, 128L << 20, null, "BASE", null, null),
                51_000_000_000L);
        var rejected = mock(ExecutionProfileService.Rejected.class);
        when(rejected.reason()).thenReturn(Reason.TRIAL_BUDGET_EXCEEDED);
        when(profiles.resolve(eq(problem.toString()), anyString(), eq(Purpose.TRIAL), any()))
                .thenThrow(rejected)
                .thenReturn(accepted);
        var response = mock(java.net.http.HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"verdict\":\"CE\",\"message\":\"invalid user code\"}".getBytes());
        var captured = new AtomicReference<java.net.http.HttpRequest>();
        var pending = new AtomicReference<java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<byte[]>>>();
        doAnswer(call -> {
                    captured.set(call.getArgument(0));
                    return pending.get() != null
                            ? pending.get()
                            : java.util.concurrent.CompletableFuture.completedFuture(response);
                })
                .when(http)
                .sendAsync(any(java.net.http.HttpRequest.class), any(java.net.http.HttpResponse.BodyHandler.class));
        var service = new CustomRunService(
                profiles, nodes, budgets, new ObjectMapper(), http, new LanguageCatalog(new ObjectMapper()));
        try {
            var unsupported = assertThrows(JudgingApiException.class, () -> service.run(request, null));
            assertEquals(422, unsupported.status().value());
            assertEquals("RUN_LIMIT_UNSUPPORTED", unsupported.code());
            verifyNoInteractions(nodes, http);

            assertEquals("COMPILE_ERROR", service.run(request, null).status());
            assertEquals(
                    java.time.Duration.ofSeconds(51), captured.get().timeout().orElseThrow());
            var tooLate = new CustomRunDtos.Request(
                    problem, "c", "int main(void){}", null, null, "", System.currentTimeMillis() + 1000);
            var late = assertThrows(JudgingApiException.class, () -> service.run(tooLate, null));
            assertEquals(504, late.status().value()); // 期限不足不是题目预算超过上限。
            verify(http, times(1))
                    .sendAsync(any(java.net.http.HttpRequest.class), any(java.net.http.HttpResponse.BodyHandler.class));
            when(profiles.resolve(eq(problem.toString()), anyString(), eq(Purpose.TRIAL), any()))
                    .thenReturn(new ExecutionProfileService.Profile(accepted.limits(), 20_000_000L));
            var future = new java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<byte[]>>();
            pending.set(future);
            var expired = assertThrows(JudgingApiException.class, () -> service.run(request, null));
            assertEquals(504, expired.status().value());
            assertTrue(future.isCancelled());
            pending.set(null);
            assertEquals("COMPILE_ERROR", service.run(request, null).status());
        } finally {
            service.close();
        }
    }

    @Test
    void utf8ClippingNeverSplitsCharacters() {
        assertEquals("中", TrialResultMapper.clip("中文", 4));
    }
}
