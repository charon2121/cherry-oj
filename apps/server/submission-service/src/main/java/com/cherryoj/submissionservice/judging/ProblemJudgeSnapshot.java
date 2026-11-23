package com.cherryoj.submissionservice.judging;

/**
 * 契约 problem-judge-snapshot：题目没有版本，也不含测试数据地址。CORE 时另有驱动代码 judgeTemplate 与该语言的前缀
 * solutionPrefix，ACM 时两者为 null；驱动代码不得写进日志。
 */
public record ProblemJudgeSnapshot(
        String problemId,
        String problemTitle,
        String languageId,
        String codeMode,
        String judgeTemplate,
        String solutionPrefix) {
    @Override
    public String toString() {
        return "ProblemJudgeSnapshot[problemId=" + problemId + ", codeMode=" + codeMode + "]";
    }
}
