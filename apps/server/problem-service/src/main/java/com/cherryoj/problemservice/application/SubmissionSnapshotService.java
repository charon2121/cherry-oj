package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguagePrefixRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguageRow;
import com.cherryoj.problemservice.storage.TestDataStore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionSnapshotService {
    private final AdminProblemMapper problems;
    private final TestDataStore testData;

    public SubmissionSnapshotService(AdminProblemMapper problems, TestDataStore testData) {
        this.problems = problems;
        this.testData = testData;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Snapshot resolve(String problemId, String languageId) {
        if (!"cpp".equals(languageId))
            throw new ProblemApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_LANGUAGE", "当前只支持 C++ 正式提交。");
        var problem = problems.findProblem(problemId);
        if (problem == null || problem.visibility() != Visibility.PUBLIC || problem.status() != ProblemStatus.ACTIVE) {
            throw unavailable();
        }
        LanguageRow language = problems.findLanguages(problemId).stream()
                .filter(row -> languageId.equals(row.languageId()))
                .findFirst()
                .orElse(null);
        if (language == null)
            throw new ProblemApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_LANGUAGE", "当前题目不支持这门语言。");
        // 测试数据读不到就不能判题：提前在提交时拒绝，而不是让提交进了队列再判成 SE。
        if (problem.testDataLocation() == null) throw unavailable();
        try {
            testData.describe(problem.testDataLocation());
        } catch (TestDataStore.AssetException error) {
            throw unavailable();
        }
        if (problem.codeMode() != CodeMode.CORE) {
            return new Snapshot(problem.id(), problem.title(), languageId, CodeMode.ACM.name(), null, null);
        }
        // 公开检查保证了驱动代码与前缀；公开之后它们缺了，按"暂时不可提交"处理，而不是送出一个编不过的程序。
        LanguagePrefixRow prefix = problems.findLanguagePrefix(languageId);
        if (language.judgeTemplate() == null || language.judgeTemplate().isBlank() || prefix == null) {
            throw unavailable();
        }
        return new Snapshot(
                problem.id(),
                problem.title(),
                languageId,
                CodeMode.CORE.name(),
                language.judgeTemplate(),
                prefix.solutionPrefix());
    }

    private static ProblemApiException unavailable() {
        return new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_AVAILABLE", "题目当前不可提交。");
    }

    /**
     * 契约见 contracts/problem-judge-snapshot.schema.json；测试数据地址不在这里，由 judging-service 判题时另取。
     * judgeTemplate（驱动代码）与 solutionPrefix 只在 CORE 出现，ACM 时连字段都不能有，所以 null 不序列化；
     * 驱动代码不得写进日志，toString 不带它们。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Snapshot(
            String problemId,
            String problemTitle,
            String languageId,
            String codeMode,
            String judgeTemplate,
            String solutionPrefix) {
        @Override
        public String toString() {
            return "Snapshot[problemId=" + problemId + ", languageId=" + languageId + ", codeMode=" + codeMode + "]";
        }
    }
}
