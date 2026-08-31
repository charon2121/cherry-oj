package com.cherryoj.problemservice.persistence;

import java.time.LocalDateTime;

public final class PublicProblemRows {

    private PublicProblemRows() {
    }

    public record ProblemRow(
            String problemId,
            String slug,
            String versionId,
            int versionNo,
            String codeMode,
            String title,
            String statementMarkdown,
            String inputDescriptionMarkdown,
            String outputDescriptionMarkdown,
            String constraintsMarkdown,
            String hintMarkdown,
            String difficulty,
            String tagsJson,
            LocalDateTime updatedAt) {
    }

    public record SampleRow(
            String versionId,
            int ordinal,
            String inputText,
            String expectedOutputText,
            String explanationMarkdown) {
    }

    public record LanguageRow(String versionId, String languageId, int displayOrder, String starterCode) {
    }
}
