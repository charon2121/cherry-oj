package com.cherryoj.problemservice.api;

import java.util.List;

public final class PublicProblemDtos {

    private PublicProblemDtos() {
    }

    public record LanguageSummary(String id, String displayName) {
    }

    public record ProblemLanguage(String id, String displayName, String starterCode) {
    }

    public record ProblemSample(int ordinal, String input, String output, String explanationMarkdown) {
    }

    public record ProblemSummary(
            String problemId,
            String slug,
            String currentVersionId,
            int versionNo,
            String title,
            String difficulty,
            List<String> tags,
            String codeMode,
            List<LanguageSummary> allowedLanguages) {
    }

    public record ProblemList(List<ProblemSummary> items, String nextCursor, boolean hasMore) {
    }

    public record ProblemDetail(
            String problemId,
            String problemVersionId,
            int versionNo,
            String slug,
            String codeMode,
            String title,
            String difficulty,
            List<String> tags,
            String statementMarkdown,
            String inputDescriptionMarkdown,
            String outputDescriptionMarkdown,
            String constraintsMarkdown,
            String hintMarkdown,
            List<ProblemSample> samples,
            List<ProblemLanguage> allowedLanguages) {
    }
}
