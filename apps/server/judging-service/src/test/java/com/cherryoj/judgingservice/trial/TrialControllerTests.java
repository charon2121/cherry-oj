package com.cherryoj.judgingservice.trial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cherryoj.judgingservice.api.*;
import com.cherryoj.judgingservice.formal.FormalProperties;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TrialControllerTests {

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
        var profiles = mock(SubmissionExecutionProfileController.class);
        var nodes = mock(JudgeNodeRepository.class);
        var problem = UUID.randomUUID();
        when(profiles.resolve(any(), any()))
                .thenReturn(new SubmissionExecutionProfileController.Profile(
                        problem.toString(),
                        "cpp",
                        "calibration",
                        new SubmissionExecutionProfileController.Limits(1000000000, 67108864, 2000000000L),
                        3000000000L));
        var node = mock(JudgeNodeRepository.Node.class);
        when(node.endpoint())
                .thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(nodes.online(eq("cpp"), any())).thenReturn(List.of(node));
        var controller =
                new TrialController(profiles, nodes, mock(FormalProperties.class), json, HttpClient.newHttpClient());
        var request = new TrialController.Request(
                problem, "cpp", "int main(){}", null, null, "", System.currentTimeMillis() + 45000);
        try {
            var result = controller.run(request, null).getBody();
            assertEquals("COMPLETED", result.status());
            assertEquals("debug", result.stderr().text());
            assertEquals("", result.stdout().text());
            payload.set("{\"verdict\":\"RAN\"}");
            assertThrows(JudgingApiException.class, () -> controller.run(request, null));
            payload.set("{\"verdict\":\"CE\",\"message\":\"/private/build/main.cpp: broken\"}");
            var ce = controller.run(request, null).getBody();
            assertEquals("COMPILE_ERROR", ce.status());
            assertNull(ce.cpuNs());
            assertFalse(ce.compileDiagnostic().contains("/private"));
            var expired = new TrialController.Request(problem, "cpp", "int main(){}", null, null, "", 1);
            assertThrows(JudgingApiException.class, () -> controller.run(expired, null));
        } finally {
            controller.close();
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
        var profiles = mock(SubmissionExecutionProfileController.class);
        var nodes = mock(JudgeNodeRepository.class);
        var problem = UUID.randomUUID();
        when(profiles.resolve(any(), any()))
                .thenReturn(new SubmissionExecutionProfileController.Profile(
                        problem.toString(),
                        "cpp",
                        "calibration",
                        new SubmissionExecutionProfileController.Limits(1000000000, 67108864, 2000000000L),
                        3000000000L));
        var node = mock(JudgeNodeRepository.Node.class);
        when(node.endpoint())
                .thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(nodes.online(eq("cpp"), any())).thenReturn(List.of(node));
        var controller =
                new TrialController(profiles, nodes, mock(FormalProperties.class), json, HttpClient.newHttpClient());
        var request = new TrialController.Request(
                problem,
                "cpp",
                "#include \"solution.cpp\"\nint main(){}",
                "#include <bits/stdc++.h>\nusing namespace std;\nclass Solution {};\n",
                2,
                "",
                System.currentTimeMillis() + 45000);
        try {
            var run = controller.run(request, null).getBody();
            assertEquals(
                    "#include <bits/stdc++.h>\nusing namespace std;\nclass Solution {};\n",
                    received.get().path("solutionSource").asText());
            assertEquals("RUNTIME_ERROR", run.status());
            assertEquals("solution.cpp:7:3: runtime error\n", run.stderr().text());
            assertEquals("solution.cpp:9\n", run.stdout().text());
            payload.set("{\"verdict\":\"CE\",\"message\":\"solution.cpp:5:20: error: x\\nMain.cpp:8:1: note\\n\"}");
            var ce = controller.run(request, null).getBody();
            assertEquals("solution.cpp:3:20: error: x\nMain.cpp:8:1: note\n", ce.compileDiagnostic());
            var missingCount = new TrialController.Request(
                    problem, "cpp", "int main(){}", "class Solution {};", null, "", System.currentTimeMillis() + 45000);
            assertThrows(JudgingApiException.class, () -> controller.run(missingCount, null));
        } finally {
            controller.close();
            server.stop(0);
        }
    }

    @Test
    void utf8ClippingNeverSplitsCharacters() {
        assertEquals("中", TrialController.clip("中文", 4));
    }
}
