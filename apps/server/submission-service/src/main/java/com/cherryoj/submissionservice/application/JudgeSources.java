package com.cherryoj.submissionservice.application;

import com.cherryoj.submissionservice.api.SubmissionException;
import com.cherryoj.submissionservice.judging.ProblemJudgeSnapshot;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/**
 * 一次判题要送出的源码。ACM 只有用户程序；CORE 是驱动文件加用户文件，合并规则见 contracts/judge-input.schema.json：
 * 用户文件 = 前缀 + 用户代码（直接拼接，前缀以换行结尾），前缀行数 = 前缀中换行符的个数。正式提交与自测共用这一处，
 * 免得两边细节不同，出现"自测能过、提交编不过"。
 *
 * @param mainFileSource 送判主文件：ACM 为用户程序，CORE 为驱动文件
 * @param solutionFileSource CORE 的用户文件（前缀 + 用户原文），ACM 为 null
 * @param solutionPrefixLineCount CORE 的前缀行数，ACM 为 null
 */
public record JudgeSources(String mainFileSource, String solutionFileSource, Integer solutionPrefixLineCount) {
    /** 与题目驱动代码的上限一致。 */
    static final int MAX_DRIVER_BYTES = 1_048_576;
    /** 用户代码 256 KiB 加上前缀 64 KiB。 */
    static final int MAX_SOLUTION_BYTES = 327_680;

    public static JudgeSources of(ProblemJudgeSnapshot snapshot, String userSource) {
        if (!"CORE".equals(snapshot.codeMode())) {
            return new JudgeSources(userSource, null, null);
        }
        String solutionFileSource = snapshot.solutionPrefix() + userSource;
        if (snapshot.judgeTemplate().getBytes(StandardCharsets.UTF_8).length > MAX_DRIVER_BYTES
                || solutionFileSource.getBytes(StandardCharsets.UTF_8).length > MAX_SOLUTION_BYTES) {
            throw new SubmissionException(HttpStatus.PAYLOAD_TOO_LARGE, "SOURCE_TOO_LARGE", "代码超过大小限制。");
        }
        int prefixLines = (int) snapshot.solutionPrefix()
                .chars()
                .filter(character -> character == '\n')
                .count();
        return new JudgeSources(snapshot.judgeTemplate(), solutionFileSource, prefixLines);
    }

    /** 快照的代码模式与它带的 CORE 字段必须一致：CORE 两者都有且前缀以换行结尾，ACM 两者都没有。 */
    public static boolean consistent(ProblemJudgeSnapshot snapshot) {
        if ("ACM".equals(snapshot.codeMode())) {
            return snapshot.judgeTemplate() == null && snapshot.solutionPrefix() == null;
        }
        return "CORE".equals(snapshot.codeMode())
                && snapshot.judgeTemplate() != null
                && !snapshot.judgeTemplate().isBlank()
                && snapshot.solutionPrefix() != null
                && snapshot.solutionPrefix().endsWith("\n");
    }

    @Override
    public String toString() {
        return "JudgeSources[hasSolutionFile=" + (solutionFileSource != null) + "]";
    }
}
