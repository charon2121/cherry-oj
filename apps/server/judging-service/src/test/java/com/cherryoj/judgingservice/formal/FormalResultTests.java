package com.cherryoj.judgingservice.formal;

import static org.junit.jupiter.api.Assertions.*;

import com.cherryoj.judgingservice.judge.JudgeGateway;
import com.cherryoj.judgingservice.judge.LanguageCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class FormalResultTests {
    private static final LanguageCatalog LANGUAGES = new LanguageCatalog(new ObjectMapper());
    private static final String SOLUTION = "#include <bits/stdc++.h>\nusing namespace std;\nclass Solution {};\n";
    private static final JudgeInput CORE = new JudgeInput(
            "s",
            "2",
            "p",
            "cpp",
            "#include \"solution.cpp\"\n",
            "x",
            SOLUTION,
            JudgeInputValidator.sha256(SOLUTION),
            2,
            "c",
            new JudgeGateway.JudgeLimits(1, 1, null),
            "BASE",
            null,
            java.time.Instant.EPOCH);

    @Test
    void coreCompileErrorsShowEditorLineNumbers() {
        var ce = new JudgeGateway.JudgeResult(
                "CE",
                null,
                null,
                null,
                "In file included from /w/Main.cpp:1:\n/w/solution.cpp:5:20: error: 'x' was not declared\n",
                null);
        assertEquals(
                "In file included from Main.cpp:1:\nsolution.cpp:3:20: error: 'x' was not declared\n",
                FormalResultMapper.toLifecycleResult(ce, 1, CORE, LANGUAGES).get("message"));
    }

    @Test
    void coreInputsMustCarryTheSolutionItsHashAndThePrefixLineCountTogether() {
        assertTrue(JudgeInputValidator.validSolutionFile(CORE, LANGUAGES));
        assertTrue(JudgeInputValidator.validSolutionFile(ACM, LANGUAGES));
        assertFalse(JudgeInputValidator.validSolutionFile(
                new JudgeInput(
                        "s",
                        "2",
                        "p",
                        "cpp",
                        "d",
                        "x",
                        SOLUTION,
                        JudgeInputValidator.sha256(SOLUTION),
                        null,
                        "c",
                        new JudgeGateway.JudgeLimits(1, 1, null),
                        "BASE",
                        null,
                        java.time.Instant.EPOCH),
                LANGUAGES));
        assertFalse(JudgeInputValidator.validSolutionFile(
                new JudgeInput(
                        "s",
                        "2",
                        "p",
                        "cpp",
                        "d",
                        "x",
                        SOLUTION,
                        "0".repeat(64),
                        2,
                        "c",
                        new JudgeGateway.JudgeLimits(1, 1, null),
                        "BASE",
                        null,
                        java.time.Instant.EPOCH),
                LANGUAGES));
        assertFalse(JudgeInputValidator.validSolutionFile(
                new JudgeInput(
                        "s",
                        "2",
                        "p",
                        "cpp",
                        "d",
                        "x",
                        null,
                        null,
                        2,
                        "c",
                        new JudgeGateway.JudgeLimits(1, 1, null),
                        "BASE",
                        null,
                        java.time.Instant.EPOCH),
                LANGUAGES));
    }

    private static final JudgeInput ACM = new JudgeInput(
            "s",
            "2",
            "p",
            "cpp",
            "int main(){}",
            "x",
            null,
            null,
            null,
            "c",
            new JudgeGateway.JudgeLimits(1, 1, null),
            "BASE",
            null,
            java.time.Instant.EPOCH);

    @Test
    void runtimeOutputIsDiscardedBeforeLifecycleProjection() {
        var json = JsonMapper.builder()
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        var result = json.readValue("""
                {"verdict":"WA","message":"hidden input printed here",
                 "testcaseResults":[{"idx":1,"verdict":"WA","name":"secret name","output":{"stdout":"hidden input"},"diff":{"expected":"secret answer"}}]}
                """, JudgeGateway.JudgeResult.class);
        var safe = FormalResultMapper.toLifecycleResult(result, 3, ACM, LANGUAGES);
        assertEquals(1, safe.get("executedTestcaseCount"));
        assertEquals(0, safe.get("passedTestcaseCount"));
        assertEquals(3, safe.get("testcaseCount"));
        String serialized = json.writeValueAsString(safe);
        assertFalse(serialized.contains("hidden"));
        assertFalse(serialized.contains("secret"));
        assertFalse(safe.containsKey("message"));
    }

    @Test
    void presentationErrorRemainsAProgramResult() {
        var safe = FormalResultMapper.toLifecycleResult(
                new JudgeGateway.JudgeResult(
                        "PE", 1L, 1L, null, null, List.of(new JudgeGateway.TestcaseResult(1, "PE", 1L, 1L))),
                1,
                ACM,
                LANGUAGES);
        assertEquals("PE", safe.get("verdict"));
        assertEquals(0, safe.get("passedTestcaseCount"));
    }

    @Test
    void compilationDiagnosticsAreBoundedAndUnknownVerdictCannotBecomeAc() {
        var ce = new JudgeGateway.JudgeResult(
                "CE",
                null,
                null,
                null,
                "/private/compiler/Main.cpp:3: error\u001b[31m\u0000\n" + "x".repeat(9000),
                null);
        String message = (String)
                FormalResultMapper.toLifecycleResult(ce, 1, ACM, LANGUAGES).get("message");
        assertTrue(message.length() <= 8192);
        assertFalse(message.contains("/private"));
        assertFalse(message.contains("\u001b"));
        assertThrows(
                FormalFailure.class,
                () -> FormalResultMapper.toLifecycleResult(
                        new JudgeGateway.JudgeResult("UNKNOWN", null, null, null), 1, ACM, LANGUAGES));
        assertThrows(
                FormalFailure.class,
                () -> FormalResultMapper.toLifecycleResult(
                        new JudgeGateway.JudgeResult(
                                "AC",
                                null,
                                null,
                                null,
                                null,
                                List.of(new JudgeGateway.TestcaseResult(2, "AC", null, null))),
                        1,
                        ACM,
                        LANGUAGES));
    }
}
