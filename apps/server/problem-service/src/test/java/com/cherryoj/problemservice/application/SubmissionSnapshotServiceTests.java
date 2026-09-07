package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.api.TestDataDtos;
import com.cherryoj.problemservice.persistence.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.cherryoj.problemservice.api.AdminProblemDtos.*;

class SubmissionSnapshotServiceTests {
    @Test void onlyPublishedAllowedReadyVersionCanBecomeSubmissionInput() {
        var problems = mock(AdminProblemMapper.class);
        var data = mock(TestDataMapper.class);
        var service = new SubmissionSnapshotService(problems, data);
        assertThrows(ProblemApiException.class, () -> service.resolve("p", "cpp"));
        assertThrows(ProblemApiException.class, () -> service.resolve("p", "java"));
        var problem = mock(AdminProblemRows.ProblemRow.class);
        when(problem.id()).thenReturn("p"); when(problem.visibility()).thenReturn(Visibility.PUBLIC);
        when(problem.status()).thenReturn(ProblemStatus.ACTIVE); when(problem.currentPublishedVersionId()).thenReturn("v1");
        when(problems.findProblem("p")).thenReturn(problem);
        var version = mock(AdminProblemRows.VersionRow.class);
        when(version.id()).thenReturn("v1"); when(version.versionNo()).thenReturn(1); when(version.title()).thenReturn("A+B");
        when(version.codeMode()).thenReturn(CodeMode.ACM); when(version.testDataVersionId()).thenReturn("d1");
        when(problems.findVersion("p", "v1")).thenReturn(version);
        assertThrows(ProblemApiException.class, () -> service.resolve("p", "cpp"));
        when(version.status()).thenReturn(VersionStatus.PUBLISHED);
        when(problems.findLanguages("v1")).thenReturn(List.of(new AdminProblemRows.LanguageRow("cpp", 0, "")));
        var ready = new TestDataRows.TestDataRow("d1", "p", TestDataDtos.Status.READY, "secret-path", "a".repeat(64), 3, 10L, "hidden-manifest", null, null, null);
        when(data.find("p", "d1")).thenReturn(ready);
        var snapshot = service.resolve("p", "cpp");
        assertEquals("v1", snapshot.problemVersionId()); assertEquals(3, snapshot.totalCount());
        assertFalse(snapshot.toString().contains("secret-path")); assertFalse(snapshot.toString().contains("hidden-manifest"));
        when(problem.visibility()).thenReturn(Visibility.PRIVATE);
        assertThrows(ProblemApiException.class, () -> service.resolve("p", "cpp"));
    }
}
