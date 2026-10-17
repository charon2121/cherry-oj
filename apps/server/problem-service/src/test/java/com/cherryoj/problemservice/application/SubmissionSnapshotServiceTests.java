package com.cherryoj.problemservice.application;

import static com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import static com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import static com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows;
import com.cherryoj.problemservice.storage.TestDataStore;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SubmissionSnapshotServiceTests {

    private final AdminProblemMapper problems = mock(AdminProblemMapper.class);
    private final TestDataStore data = mock(TestDataStore.class);
    private final SubmissionSnapshotService service = new SubmissionSnapshotService(problems, data);

    @Test
    void onlyAPublicActiveAllowedProblemWithReadableTestDataBecomesASubmissionSnapshot() throws Exception {
        // 题目不存在、语言不支持
        assertThrows(ProblemApiException.class, () -> service.resolve("p", "cpp"));
        assertThrows(ProblemApiException.class, () -> service.resolve("p", "java"));

        var problem = mock(AdminProblemRows.ProblemRow.class);
        when(problem.id()).thenReturn("p");
        when(problem.title()).thenReturn("A+B");
        when(problem.visibility()).thenReturn(Visibility.PUBLIC);
        when(problem.status()).thenReturn(ProblemStatus.ACTIVE);
        when(problem.codeMode()).thenReturn(CodeMode.ACM);
        when(problem.testDataLocation()).thenReturn("/srv/problem/p");
        when(problems.findProblem("p")).thenReturn(problem);
        when(problems.findLanguages("p")).thenReturn(List.of(new AdminProblemRows.LanguageRow("cpp", 1, "", null, null)));

        // 测试数据读不到：提交时就拒绝，而不是让它进队列后判成 SE
        when(data.describe(anyString())).thenThrow(new TestDataStore.AssetException(
                TestDataStore.Kind.NOT_FOUND, "TEST_DATA_NOT_FOUND"));
        assertThrows(ProblemApiException.class, () -> service.resolve("p", "cpp"));

        // 再配具体的桩时不能在 when(...) 里调用已经被模糊桩住的方法，改用 doReturn
        doReturn(new TestDataStore.Info("a".repeat(64), 3, 10L, null)).when(data).describe("/srv/problem/p");
        var snapshot = service.resolve("p", "cpp");
        assertEquals("p", snapshot.problemId());
        assertEquals("A+B", snapshot.problemTitle());
        assertEquals("ACM", snapshot.codeMode());
        // 快照里不带测试数据地址：它只在判题时由 judging-service 另取。
        assertFalse(snapshot.toString().contains("/srv/problem"));

        when(problem.visibility()).thenReturn(Visibility.PRIVATE);
        assertThrows(ProblemApiException.class, () -> service.resolve("p", "cpp"));
    }

    @Test
    void aCoreSnapshotCarriesTheDriverAndThePrefixButNeverPrintsThem() throws Exception {
        var problem = publicProblem(CodeMode.CORE);
        String driver = "#include \"solution.cpp\"\nint main() { /* driver-canary */ }\n";
        when(problems.findLanguages("p")).thenReturn(List.of(
                new AdminProblemRows.LanguageRow("cpp", 1, "", driver, LocalDateTime.of(2026, 10, 17, 0, 0))));
        when(problems.findLanguagePrefix("cpp")).thenReturn(new AdminProblemRows.LanguagePrefixRow(
                "cpp", "C++", "#include <bits/stdc++.h>\nusing namespace std;\n", LocalDateTime.of(2026, 10, 1, 0, 0)));

        var snapshot = service.resolve("p", "cpp");

        assertEquals("CORE", snapshot.codeMode());
        assertEquals(driver, snapshot.judgeTemplate());
        assertEquals("#include <bits/stdc++.h>\nusing namespace std;\n", snapshot.solutionPrefix());
        assertFalse(snapshot.toString().contains("driver-canary"));
    }

    @Test
    void aCoreProblemWithoutADriverOrPrefixIsUnavailable() throws Exception {
        publicProblem(CodeMode.CORE);
        when(problems.findLanguages("p")).thenReturn(List.of(new AdminProblemRows.LanguageRow("cpp", 1, "", null, null)));
        assertThrows(ProblemApiException.class, () -> service.resolve("p", "cpp"));

        when(problems.findLanguages("p")).thenReturn(List.of(
                new AdminProblemRows.LanguageRow("cpp", 1, "", "int main() {}", LocalDateTime.of(2026, 10, 17, 0, 0))));
        assertThrows(ProblemApiException.class, () -> service.resolve("p", "cpp"));
    }

    /** 契约规定 ACM 快照不能出现 judgeTemplate、solutionPrefix 字段，连 null 都不行。 */
    @Test
    void anAcmSnapshotSerializesWithoutCoreFields() throws Exception {
        publicProblem(CodeMode.ACM);
        when(problems.findLanguages("p")).thenReturn(List.of(new AdminProblemRows.LanguageRow("cpp", 1, "", null, null)));

        String json = JsonMapper.builder().build().writeValueAsString(service.resolve("p", "cpp"));

        assertFalse(json.contains("judgeTemplate"), json);
        assertFalse(json.contains("solutionPrefix"), json);
    }

    private AdminProblemRows.ProblemRow publicProblem(CodeMode codeMode) throws Exception {
        var problem = mock(AdminProblemRows.ProblemRow.class);
        when(problem.id()).thenReturn("p");
        when(problem.title()).thenReturn("A+B");
        when(problem.visibility()).thenReturn(Visibility.PUBLIC);
        when(problem.status()).thenReturn(ProblemStatus.ACTIVE);
        when(problem.codeMode()).thenReturn(codeMode);
        when(problem.testDataLocation()).thenReturn("/srv/problem/p");
        when(problems.findProblem("p")).thenReturn(problem);
        doReturn(new TestDataStore.Info("a".repeat(64), 3, 10L, null)).when(data).describe("/srv/problem/p");
        return problem;
    }

    @Test
    void aProblemWithoutTestDataIsUnavailable() {
        var problem = mock(AdminProblemRows.ProblemRow.class);
        when(problem.visibility()).thenReturn(Visibility.PUBLIC);
        when(problem.status()).thenReturn(ProblemStatus.ACTIVE);
        when(problem.codeMode()).thenReturn(CodeMode.ACM);
        when(problems.findProblem("p")).thenReturn(problem);
        when(problems.findLanguages("p")).thenReturn(List.of(new AdminProblemRows.LanguageRow("cpp", 1, "", null, null)));
        assertThrows(ProblemApiException.class, () -> service.resolve("p", "cpp"));
    }
}
