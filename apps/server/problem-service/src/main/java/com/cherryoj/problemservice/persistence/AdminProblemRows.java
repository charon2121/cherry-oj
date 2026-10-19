package com.cherryoj.problemservice.persistence;

import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.Difficulty;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import java.time.LocalDateTime;

public final class AdminProblemRows {

    private AdminProblemRows() {}

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
            long rowVersion) {}

    public record SampleRow(int ordinal, String inputText, String expectedOutputText, String explanationMarkdown) {}

    /** judgeTemplate 是 CORE 的驱动代码，ACM 或尚未填写时为 null；judgeTemplateUpdatedAt 与它同时为 null。 */
    public record LanguageRow(
            String languageId,
            int displayOrder,
            String starterCode,
            String judgeTemplate,
            LocalDateTime judgeTemplateUpdatedAt) {}

    /** 语言表的一行：solutionPrefix 是所有 CORE 题目共用的前缀代码，以换行结尾。 */
    public record LanguagePrefixRow(
            String languageId, String displayName, String solutionPrefix, LocalDateTime updatedAt) {}
}
