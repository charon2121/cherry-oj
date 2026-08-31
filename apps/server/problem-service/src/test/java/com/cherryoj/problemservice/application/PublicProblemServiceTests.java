package com.cherryoj.problemservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.persistence.PublicProblemMapper;
import com.cherryoj.problemservice.persistence.PublicProblemRows.LanguageRow;
import com.cherryoj.problemservice.persistence.PublicProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.PublicProblemRows.SampleRow;
import com.cherryoj.problemservice.persistence.PublicProblemSearch;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class PublicProblemServiceTests {

    private final PublicProblemMapper mapper = mock(PublicProblemMapper.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final ProblemCursorCodec cursors = new ProblemCursorCodec(json);
    private final PublicProblemService service = new PublicProblemService(mapper, cursors, json);

    @BeforeEach
    void languages() {
        when(mapper.findLanguages(any())).thenAnswer(invocation -> ((List<String>) invocation.getArgument(0)).stream()
                .map(id -> new LanguageRow(id, "cpp", 1, "int main() {}"))
                .toList());
    }

    @Test
    void listUsesSizePlusOneAndIssuesFilterBoundCursor() {
        when(mapper.findPublicProblems(any())).thenReturn(List.of(
                row("101", "alpha", "2026-08-30T01:00:03"),
                row("102", "beta", "2026-08-30T01:00:02"),
                row("103", "gamma", "2026-08-30T01:00:01")));

        var page = service.list(null, null, List.of("入门"), null, "cpp",
                PublicProblemService.Sort.UPDATED_DESC, null, 2);

        assertThat(page.items()).extracting(item -> item.slug()).containsExactly("alpha", "beta");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
        assertThat(page.items().getFirst().allowedLanguages().getFirst().displayName()).isEqualTo("C++");

        ArgumentCaptor<PublicProblemSearch> search = ArgumentCaptor.forClass(PublicProblemSearch.class);
        verify(mapper).findPublicProblems(search.capture());
        assertThat(search.getValue().limit()).isEqualTo(3);
        assertThatThrownBy(() -> service.list(null, null, List.of("不同标签"), null, "cpp",
                PublicProblemService.Sort.UPDATED_DESC, page.nextCursor(), 2))
                .isInstanceOf(ProblemApiException.class)
                .hasMessageContaining("游标");
    }

    @Test
    void detailOnlySerializesThePublicFieldWhitelist() throws Exception {
        ProblemRow row = row("101", "a-plus-b", "2026-08-30T01:00:03");
        when(mapper.findPublicProblemBySlug("a-plus-b")).thenReturn(row);
        when(mapper.findSamples(row.versionId())).thenReturn(List.of(
                new SampleRow(row.versionId(), 1, "1 2\n", "3\n", null)));

        String body = json.writeValueAsString(service.detail("a-plus-b"));

        assertThat(body).contains("a-plus-b", "statementMarkdown", "allowedLanguages");
        assertThat(body).doesNotContain(
                "judgeTemplate", "testDataVersionId", "storageRef", "manifest", "createdBy", "publishedBy", "audit");
    }

    @Test
    void missingOrInvisibleProblemHasOneNotFoundShape() {
        when(mapper.findPublicProblemBySlug("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.detail("missing"))
                .isInstanceOfSatisfying(ProblemApiException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(404);
                    assertThat(error.code()).isEqualTo("PROBLEM_NOT_FOUND");
                });
    }

    private static ProblemRow row(String suffix, String slug, String updatedAt) {
        String id = "019c8e42-7f70-7000-8000-000000000" + suffix;
        String versionId = "019c8e42-7f70-7000-8000-000000001" + suffix;
        return new ProblemRow(
                id, slug, versionId, 1, "ACM", slug, "statement", "input", "output", null, null,
                "EASY", "[\"入门\"]", LocalDateTime.parse(updatedAt));
    }
}
