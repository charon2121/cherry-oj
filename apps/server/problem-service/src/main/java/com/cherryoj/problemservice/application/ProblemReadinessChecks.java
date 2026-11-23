package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.AdminProblemDtos;
import com.cherryoj.problemservice.api.AdminProblemDtos.PublishCheckCode;
import com.cherryoj.problemservice.api.AdminProblemDtos.PublishCheckItem;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguagePrefixRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguageRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.SampleRow;
import com.cherryoj.problemservice.storage.TestDataStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 公开和标定共用的题目快照与本地就绪条件。 */
@Component
class ProblemReadinessChecks {
    private final AdminProblemMapper problems;
    private final TestDataStore testData;

    ProblemReadinessChecks(AdminProblemMapper problems, TestDataStore testData) {
        this.problems = problems;
        this.testData = testData;
    }

    Snapshot load(String problemId, boolean lock) {
        ProblemRow problem = lock ? problems.findProblemForUpdate(problemId) : problems.findProblem(problemId);
        if (problem == null) throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "题目不存在。");
        TestDataStore.Info info = null;
        String dataProblem = "还没有上传测试数据。";
        if (problem.testDataLocation() != null) {
            try {
                info = testData.describe(problem.testDataLocation());
            } catch (TestDataStore.AssetException error) {
                dataProblem = "测试数据读取不到或已损坏，请重新上传。";
            }
        }
        List<LanguageRow> languages = problems.findLanguages(problemId);
        Map<String, LanguagePrefixRow> prefixes = new LinkedHashMap<>();
        for (var language : languages) {
            var prefix = problems.findLanguagePrefix(language.languageId());
            if (prefix != null) prefixes.put(language.languageId(), prefix);
        }
        return new Snapshot(problem, problems.findSamples(problemId), languages, prefixes, info, dataProblem);
    }

    static void requireActive(ProblemRow problem) {
        if (problem.status() != AdminProblemDtos.ProblemStatus.ACTIVE) throw state("归档题目不能标定或公开。");
    }

    static List<PublishCheckItem> localChecks(Snapshot local) {
        ProblemRow problem = local.problem();
        boolean content = nonBlank(problem.title())
                && nonBlank(problem.statementMarkdown())
                && nonBlank(problem.inputDescriptionMarkdown())
                && nonBlank(problem.outputDescriptionMarkdown());
        boolean samples = !local.samples().isEmpty() && continuous(local.samples());
        String languageProblem = languageProblem(local);
        boolean language = languageProblem == null;
        boolean data = local.info() != null;
        ArrayList<PublishCheckItem> checks = new ArrayList<>(6);
        checks.add(item(PublishCheckCode.CONTENT, content, content ? "题面必填章节完整。" : "标题、题面、输入或输出说明不完整。"));
        checks.add(item(PublishCheckCode.SAMPLES, samples, samples ? "已有样例。" : "至少需要一个样例。"));
        checks.add(item(
                PublishCheckCode.LANGUAGE,
                language,
                language
                        ? (problem.codeMode() == AdminProblemDtos.CodeMode.CORE ? "核心代码语言配置有效。" : "ACM 语言配置有效。")
                        : languageProblem));
        checks.add(item(
                PublishCheckCode.TEST_DATA,
                data,
                data ? "测试数据就绪：" + local.info().testcaseCount() + " 个测试点。" : local.dataProblem()));
        return checks;
    }

    /** 语言配置不能公开的原因；可以公开时返回 null。 */
    private static String languageProblem(Snapshot local) {
        if (local.languages().isEmpty()) return "请先配置语言。";
        return local.languages().stream().anyMatch(language -> languageProblem(local, language) == null)
                ? null
                : languageProblem(local, local.languages().getFirst());
    }

    static String languageProblem(Snapshot local, LanguageRow language) {
        if (local.problem().codeMode() != AdminProblemDtos.CodeMode.CORE) return null;
        if (!nonBlank(language.judgeTemplate())) return "核心代码题需要填写驱动代码。";
        if (!local.prefixes().containsKey(language.languageId())) return "缺少该语言的前缀代码配置。";
        return null;
    }

    private static boolean continuous(List<SampleRow> samples) {
        for (int index = 0; index < samples.size(); index++) {
            if (samples.get(index).ordinal() != index + 1) return false;
        }
        return true;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static PublishCheckItem item(PublishCheckCode code, boolean passed, String message) {
        return new PublishCheckItem(code, passed, message);
    }

    static ProblemApiException state(String message) {
        return new ProblemApiException(HttpStatus.CONFLICT, "RESOURCE_STATE_CONFLICT", message);
    }

    /** info 为 null 表示测试数据缺失或读不到，原因在 dataProblem 里；prefixes 按语言保存当前平台前缀。 */
    record Snapshot(
            ProblemRow problem,
            List<SampleRow> samples,
            List<LanguageRow> languages,
            Map<String, LanguagePrefixRow> prefixes,
            TestDataStore.Info info,
            String dataProblem) {}
}
