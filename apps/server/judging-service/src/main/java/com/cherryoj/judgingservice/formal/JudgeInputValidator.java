package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.api.JudgingDtos;
import com.cherryoj.judgingservice.judge.LanguageCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

final class JudgeInputValidator {
    private JudgeInputValidator() {}

    static void validate(JudgeInput judgeInput, String expectedSubmissionId, LanguageCatalog languages) {
        // CORE 的 completeSource 是驱动文件，上限与题目的驱动代码一致（1 MiB）；用户代码的上限在提交入口检查。
        if (judgeInput == null
                || !expectedSubmissionId.equals(judgeInput.submissionId())
                || !"2".equals(judgeInput.contractVersion())
                || judgeInput.languageId() == null
                || !judgeInput.languageId().matches(JudgingDtos.LANGUAGE_PATTERN)
                || judgeInput.completeSource() == null
                || judgeInput.completeSource().getBytes(StandardCharsets.UTF_8).length > 1_048_576
                || judgeInput.sourceSha256() == null
                || !sha256(judgeInput.completeSource()).equals(judgeInput.sourceSha256())
                || !validSolutionFile(judgeInput, languages)
                || judgeInput.effectiveLimits() == null
                || judgeInput.effectiveLimits().cpuNs() <= 0
                || judgeInput.effectiveLimits().memoryBytes() <= 0
                || (judgeInput.effectiveLimits().clockNs() != null
                        && judgeInput.effectiveLimits().clockNs() <= 0)
                || !validLimitSource(judgeInput)
                || judgeInput.createdAt() == null) throw new FormalFailure("INVALID_JUDGE_INPUT");
        try {
            for (String uuid : List.of(judgeInput.problemId(), judgeInput.languageCalibrationId()))
                UUID.fromString(uuid);
        } catch (RuntimeException error) {
            throw new FormalFailure("INVALID_JUDGE_INPUT");
        }
    }
    /** 推导依据只在 DERIVED 时出现（docs/protocols/limits.md 第 6 节）。 */
    static boolean validLimitSource(JudgeInput judgeInput) {
        if (judgeInput.limitSource() == null) return false;
        return switch (judgeInput.limitSource()) {
            case "BASE", "OVERRIDE" -> judgeInput.limitDerivation() == null;
            case "DERIVED" -> judgeInput.limitDerivation() != null;
            default -> false;
        };
    }

    /** 用户文件、摘要、前缀行数三者同时出现（CORE）或同时缺席（ACM），且用户文件与摘要一致。 */
    static boolean validSolutionFile(JudgeInput judgeInput, LanguageCatalog languages) {
        String solutionFileSource = judgeInput.solutionSource();
        if (solutionFileSource == null)
            return judgeInput.solutionSha256() == null && judgeInput.solutionPrefixLineCount() == null;
        return judgeInput.solutionSha256() != null
                && sha256(solutionFileSource).equals(judgeInput.solutionSha256())
                && judgeInput.solutionPrefixLineCount() != null
                && judgeInput.solutionPrefixLineCount() >= 0
                && languages.coreFiles(judgeInput.languageId()) != null
                && solutionFileSource.getBytes(StandardCharsets.UTF_8).length <= 327_680;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
