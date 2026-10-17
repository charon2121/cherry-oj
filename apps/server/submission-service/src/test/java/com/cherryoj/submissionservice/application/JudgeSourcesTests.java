package com.cherryoj.submissionservice.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cherryoj.submissionservice.api.SubmissionDtos.Snapshot;
import com.cherryoj.submissionservice.api.SubmissionException;
import org.junit.jupiter.api.Test;

class JudgeSourcesTests {
    private static final String PREFIX = "#include <bits/stdc++.h>\nusing namespace std;\n";
    private static final String DRIVER = "#include \"solution.cpp\"\nint main() {}\n";

    @Test
    void acmSendsTheUserProgramAsIs() {
        var sources = JudgeSources.of(new Snapshot("p", "t", "cpp", "ACM", null, null), "int main() {}");

        assertEquals("int main() {}", sources.source());
        assertNull(sources.solutionSource());
        assertNull(sources.solutionPrefixLineCount());
    }

    /** 合并规则写在 contracts/judge-input.schema.json：直接拼接，不加换行；前缀行数是前缀里的换行符个数。 */
    @Test
    void coreSendsTheDriverAndThePrefixGluedToTheUserCode() {
        var sources = JudgeSources.of(new Snapshot("p", "t", "cpp", "CORE", DRIVER, PREFIX), "class Solution {};");

        assertEquals(DRIVER, sources.source());
        assertEquals(PREFIX + "class Solution {};", sources.solutionSource());
        assertEquals(2, sources.solutionPrefixLineCount());
        assertFalse(sources.toString().contains("Solution {}"));
    }

    @Test
    void aSolutionFileOverTheLimitIsRejected() {
        var snapshot = new Snapshot("p", "t", "cpp", "CORE", DRIVER, PREFIX);
        String userCode = "x".repeat(JudgeSources.MAX_SOLUTION_BYTES - PREFIX.length() + 1);

        var error = assertThrows(SubmissionException.class, () -> JudgeSources.of(snapshot, userCode));
        assertEquals("SOURCE_TOO_LARGE", error.code());
    }

    @Test
    void theCodeModeMustAgreeWithTheCoreFields() {
        assertTrue(JudgeSources.consistent(new Snapshot("p", "t", "cpp", "ACM", null, null)));
        assertTrue(JudgeSources.consistent(new Snapshot("p", "t", "cpp", "CORE", DRIVER, PREFIX)));
        assertFalse(JudgeSources.consistent(new Snapshot("p", "t", "cpp", "ACM", DRIVER, null)));
        assertFalse(JudgeSources.consistent(new Snapshot("p", "t", "cpp", "CORE", null, PREFIX)));
        assertFalse(JudgeSources.consistent(new Snapshot("p", "t", "cpp", "CORE", " ", PREFIX)));
        assertFalse(JudgeSources.consistent(new Snapshot("p", "t", "cpp", "CORE", DRIVER, "no trailing newline")));
        assertFalse(JudgeSources.consistent(new Snapshot("p", "t", "cpp", "OTHER", null, null)));
    }
}
