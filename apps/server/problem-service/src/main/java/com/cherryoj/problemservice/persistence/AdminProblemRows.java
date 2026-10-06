package com.cherryoj.problemservice.persistence;

import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.Difficulty;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import java.time.LocalDateTime;

public final class AdminProblemRows {

    private AdminProblemRows() {
    }

    public record ProblemRow(
            String id,
            String slug,
            Visibility visibility,
            ProblemStatus status,
            CodeMode codeMode,
            String title,
            String statementMarkdown,
            String inputDescriptionMarkdown,
            String outputDescriptionMarkdown,
            String constraintsMarkdown,
            String hintMarkdown,
            Difficulty difficulty,
            String tagsJson,
            String testDataLocation,
            LocalDateTime testDataUpdatedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime publishedAt,
            long rowVersion) {
    }

    public record SampleRow(int ordinal, String inputText, String expectedOutputText, String explanationMarkdown) {
    }

    public record LanguageRow(String languageId, int displayOrder, String starterCode) {
    }
}
