package com.cherryoj.judgingservice.trial;

import static com.cherryoj.judgingservice.trial.CustomRunErrors.unavailable;

import com.cherryoj.judgingservice.judge.JudgeDiagnostics;
import com.cherryoj.judgingservice.trial.CustomRunDtos.EffectiveLimits;
import com.cherryoj.judgingservice.trial.CustomRunDtos.Output;
import com.cherryoj.judgingservice.trial.CustomRunDtos.Result;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;

final class TrialResultMapper {
    private TrialResultMapper() {}

    static Result fromJudge(JsonNode tree, EffectiveLimits effective, String solutionFile, Integer prefixLines) {
        String verdict = tree.path("verdict").asText();
        if ("CE".equals(verdict))
            return new Result(
                    "COMPILE_ERROR",
                    null,
                    null,
                    null,
                    null,
                    diagnostic(tree.path("message").asText(""), solutionFile, prefixLines),
                    effective);
        String status = switch (verdict) {
            case "RAN" -> "COMPLETED";
            case "RE" -> "RUNTIME_ERROR";
            case "TLE" -> "TIME_LIMIT_EXCEEDED";
            case "MLE" -> "MEMORY_LIMIT_EXCEEDED";
            case "OLE" -> "OUTPUT_LIMIT_EXCEEDED";
            default -> throw unavailable();
        };
        var testcases = tree.path("testcaseResults");
        if (!testcases.isArray() || testcases.size() != 1) throw unavailable();
        var c = testcases.get(0);
        if (!verdict.equals(c.path("verdict").asText()) || c.path("idx").asInt() != 1) throw unavailable();
        // Missing streams indicate an old judge binary; never pretend stderr was
        // empty.
        // 标准输出是程序自己打印的，原样返回；stderr 里的运行错误位置同样换算。
        return new Result(
                status,
                nonnegative(c, "cpuNs"),
                nonnegative(c, "memoryBytes"),
                output(c.path("output")),
                stderr(output(c.path("stderr")), solutionFile, prefixLines),
                null,
                effective);
    }

    private static long nonnegative(JsonNode n, String field) {
        var v = n.path(field);
        if (v.isMissingNode()) return 0;
        if (!v.isIntegralNumber() || !v.canConvertToLong() || v.longValue() < 0) throw unavailable();
        return v.longValue();
    }

    private static Output output(JsonNode n) {
        if (!n.isObject() || !n.path("excerpt").isString() || !n.path("bytes").isIntegralNumber()) throw unavailable();
        String text = n.path("excerpt").asText();
        long bytes = nonnegative(n, "bytes");
        if (bytes < text.getBytes(StandardCharsets.UTF_8).length) throw unavailable();
        return new Output(
                clip(text, 16384),
                bytes,
                n.path("truncated").asBoolean(false) || text.getBytes(StandardCharsets.UTF_8).length > 16384);
    }

    static String clip(String text, int max) {
        int end = 0, count = 0;
        while (end < text.length()) {
            int cp = text.codePointAt(end);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (count + size > max) break;
            count += size;
            end += Character.charCount(cp);
        }
        return text.substring(0, end);
    }

    private static String diagnostic(String text, String solutionFile, Integer prefixLines) {
        return clip(JudgeDiagnostics.forUser(text, solutionFile, prefixLines, JudgeDiagnostics.looksCut(text)), 8192);
    }

    private static Output stderr(Output raw, String solutionFile, Integer prefixLines) {
        if (solutionFile == null) return raw;
        return new Output(
                JudgeDiagnostics.forUser(raw.text(), solutionFile, prefixLines, raw.truncated()),
                raw.capturedBytes(),
                raw.truncated());
    }
}
