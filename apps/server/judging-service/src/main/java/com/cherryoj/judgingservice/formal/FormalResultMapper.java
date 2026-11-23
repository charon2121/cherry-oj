package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.judge.JudgeDiagnostics;
import com.cherryoj.judgingservice.judge.JudgeGateway;
import com.cherryoj.judgingservice.judge.LanguageCatalog;
import com.cherryoj.judgingservice.judge.LanguageFiles;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class FormalResultMapper {
    private FormalResultMapper() {}

    static Map<String, Object> toLifecycleResult(
            JudgeGateway.JudgeResult result, int testcaseCount, FormalInput input, LanguageCatalog languages) {
        Set<String> verdicts = Set.of("AC", "WA", "PE", "TLE", "MLE", "OLE", "RE", "CE");
        if (result == null || result.verdict() == null || !verdicts.contains(result.verdict()))
            throw new FormalFailure("INVALID_JUDGE_RESULT");
        var safe = new LinkedHashMap<String, Object>();
        safe.put("verdict", result.verdict());
        if (result.cpuNs() != null) {
            if (result.cpuNs() < 0) throw new FormalFailure("INVALID_JUDGE_RESULT");
            safe.put("cpuNs", result.cpuNs());
        }
        if (result.memoryBytes() != null) {
            if (result.memoryBytes() < 0) throw new FormalFailure("INVALID_JUDGE_RESULT");
            safe.put("memoryBytes", result.memoryBytes());
        }
        if (result.testcaseResults() != null) {
            if (result.testcaseResults().size() > testcaseCount) throw new FormalFailure("INVALID_JUDGE_RESULT");
            int passed = 0, index = 0;
            for (var test : result.testcaseResults()) {
                if (test.idx() != ++index || !verdicts.contains(test.verdict()))
                    throw new FormalFailure("INVALID_JUDGE_RESULT");
                if ("AC".equals(test.verdict())) passed++;
            }
            safe.put("passedTestcaseCount", passed);
            safe.put("executedTestcaseCount", index);
            safe.put("testcaseCount", testcaseCount);
        }
        if ("CE".equals(result.verdict())
                && result.message() != null
                && !result.message().isBlank()) {
            // CORE：用户文件的行号按冻结的前缀行数换算成编辑器行号；路径只留文件名。
            LanguageFiles files = input.solutionSource() == null ? null : languages.coreFiles(input.languageId());
            String diagnostic = JudgeDiagnostics.forUser(
                    result.message(),
                    files == null ? null : files.solutionFile(),
                    files == null ? null : input.solutionPrefixLineCount(),
                    JudgeDiagnostics.looksCut(result.message()));
            safe.put("message", diagnostic.substring(0, Math.min(diagnostic.length(), 8192)));
        }
        return safe;
    }
}
