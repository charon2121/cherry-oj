package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.storage.TestDataStore;
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
        if (!"cpp".equals(languageId)) throw new ProblemApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "UNSUPPORTED_LANGUAGE", "当前只支持 C++ ACM 正式提交。");
        var problem = problems.findProblem(problemId);
        if (problem == null || problem.visibility() != Visibility.PUBLIC || problem.status() != ProblemStatus.ACTIVE) {
            throw unavailable();
        }
        if (problem.codeMode() != CodeMode.ACM || problems.findLanguages(problemId).stream()
                .noneMatch(l -> languageId.equals(l.languageId()))) throw new ProblemApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_CODE_MODE", "当前题目不支持 C++ ACM 正式提交。");
        // 测试数据读不到就不能判题：提前在提交时拒绝，而不是让提交进了队列再判成 SE。
        if (problem.testDataLocation() == null) throw unavailable();
        try {
            testData.describe(problem.testDataLocation());
        }
        catch (TestDataStore.AssetException error) {
            throw unavailable();
        }
        return new Snapshot(problem.id(), problem.title(), languageId, "ACM");
    }

    private static ProblemApiException unavailable() {
        return new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_AVAILABLE", "题目当前不可提交。");
    }

    /** 契约见 contracts/problem-judge-snapshot.schema.json；测试数据地址不在这里，由 judging-service 判题时另取。 */
    public record Snapshot(String problemId, String problemTitle, String languageId, String codeMode) {}
}
