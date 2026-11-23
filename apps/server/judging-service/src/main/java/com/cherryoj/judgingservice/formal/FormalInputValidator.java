package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.api.JudgingDtos;
import com.cherryoj.judgingservice.judge.LanguageCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

final class FormalInputValidator {
    private FormalInputValidator() {}

    static void validate(FormalInput input, String id, LanguageCatalog languages) {
        // CORE 的 completeSource 是驱动文件，上限与题目的驱动代码一致（1 MiB）；用户代码的上限在提交入口检查。
        if (input == null
                || !id.equals(input.submissionId())
                || !"2".equals(input.contractVersion())
                || input.languageId() == null
                || !input.languageId().matches(JudgingDtos.LANGUAGE_PATTERN)
                || input.completeSource() == null
                || input.completeSource().getBytes(StandardCharsets.UTF_8).length > 1_048_576
                || input.sourceSha256() == null
                || !hash(input.completeSource()).equals(input.sourceSha256())
                || !validSolution(input, languages)
                || input.effectiveLimits() == null
                || input.effectiveLimits().cpuNs() <= 0
                || input.effectiveLimits().memoryBytes() <= 0
                || (input.effectiveLimits().clockNs() != null
                        && input.effectiveLimits().clockNs() <= 0)
                || !validLimitSource(input)
                || input.createdAt() == null) throw new FormalFailure("INVALID_JUDGE_INPUT");
        try {
            for (String uuid : List.of(input.problemId(), input.languageCalibrationId())) UUID.fromString(uuid);
        } catch (RuntimeException error) {
            throw new FormalFailure("INVALID_JUDGE_INPUT");
        }
    }
    /** 推导依据只在 DERIVED 时出现（docs/protocols/limits.md 第 6 节）。 */
    static boolean validLimitSource(FormalInput input) {
        if (input.limitSource() == null) return false;
        return switch (input.limitSource()) {
            case "BASE", "OVERRIDE" -> input.limitDerivation() == null;
            case "DERIVED" -> input.limitDerivation() != null;
            default -> false;
        };
    }

    /** 用户文件、摘要、前缀行数三者同时出现（CORE）或同时缺席（ACM），且用户文件与摘要一致。 */
    static boolean validSolution(FormalInput input, LanguageCatalog languages) {
        if (input.solutionSource() == null)
            return input.solutionSha256() == null && input.solutionPrefixLineCount() == null;
        return input.solutionSha256() != null
                && hash(input.solutionSource()).equals(input.solutionSha256())
                && input.solutionPrefixLineCount() != null
                && input.solutionPrefixLineCount() >= 0
                && languages.coreFiles(input.languageId()) != null
                && input.solutionSource().getBytes(StandardCharsets.UTF_8).length <= 327_680;
    }

    static String hash(String source) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
