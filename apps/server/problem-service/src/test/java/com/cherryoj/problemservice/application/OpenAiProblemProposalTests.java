package com.cherryoj.problemservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.config.AuthoringProperties;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguagePrefixRow;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpenAiProblemProposalTests {
    private static final String CANDIDATE = """
            {"title":"求和","statementMarkdown":"题面","inputDescriptionMarkdown":"输入","outputDescriptionMarkdown":"输出",
             "constraintsMarkdown":"","samples":[{"ordinal":1,"input":"1 2","output":"3","explanationMarkdown":null}],
             "languages":[{"id":"cpp","starterCode":"","judgeTemplate":null}],
             "input":{"generatorSource":"generator","validatorSource":"validator","oracleSource":"oracle",
             "answerLanguageId":"cpp","cases":[{"name":"one","generatorInput":"1 2","oracle":true,"sampleOrdinal":1}],
             "languages":[{"languageId":"cpp","limits":{"cpuNs":1000000000,"clockNs":3000000000,"memoryBytes":268435456},
             "referenceSource":"reference","wrongSolutions":[{"source":"wrong","witnessCaseName":"one"}]}]}}
            """;

    @Test
    void sendsOnlyExplicitBriefAndReturnsAValidatedCandidateWithoutApplyingIt() throws Exception {
        var json = new ObjectMapper();
        var problems = mock(AdminProblemMapper.class);
        when(problems.findLanguagePrefix("cpp"))
                .thenReturn(new LanguagePrefixRow("cpp", "C++", "#include <iostream>\n", LocalDateTime.now()));
        var inputs = new AuthoringInput(problems, json);
        var frozen = inputs.proposal(
                json.readTree("""
                {"kind":"PROPOSAL","rowVersion":7,"knowledgePoint":"前缀和","difficulty":"EASY","goal":"练习区间"}
                """),
                new AuthoringInput.Snapshot(
                        CodeMode.CORE, "private-statement", "private-snapshot", List.of(), Map.of()),
                id -> {
                    throw new AssertionError("unexpected proposal lookup");
                });
        var sent = new AtomicReference<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/v1/responses", exchange -> {
                try (exchange) {
                    sent.set(json.readTree(exchange.getRequestBody()));
                    var response = json.createObjectNode().put("status", "completed");
                    response.putArray("output")
                            .addObject()
                            .put("type", "message")
                            .putArray("content")
                            .addObject()
                            .put("type", "output_text")
                            .put("text", CANDIDATE);
                    byte[] body = json.writeValueAsBytes(response);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
            var settings = new AuthoringProperties(null, null, null, null, "test-model", "test-key");
            var adapter = new OpenAiProblemProposal(
                    settings,
                    json,
                    inputs,
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses"));
            var outcome = adapter.generate(frozen.json(), () -> true);
            assertThat(outcome.status()).isEqualTo("SUCCEEDED");
            assertThat(outcome.archive()).isNull();
            assertThat(json.readTree(outcome.data()).path("title").asText()).isEqualTo("求和");
            assertThat(sent.get().path("store").asBoolean(true)).isFalse();
            assertThat(sent.get().has("tools")).isFalse();
            var outgoing = json.readTree(sent.get().path("input").asText());
            assertThat(outgoing.propertyNames()).containsExactlyInAnyOrder("knowledgePoint", "difficulty", "goal");
            assertThat(sent.get().toString()).doesNotContain("private-statement", "private-snapshot", "test-key");
        } finally {
            server.stop(0);
        }
    }
}
