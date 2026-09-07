package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.TestDataMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.cherryoj.problemservice.api.AdminProblemDtos.*;

@Service
public class SubmissionSnapshotService {
    private final AdminProblemMapper problems;
    private final TestDataMapper data;
    public SubmissionSnapshotService(AdminProblemMapper problems, TestDataMapper data) {
        this.problems = problems; this.data = data;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Snapshot resolve(String problemId, String languageId) {
        if (!"cpp".equals(languageId)) throw new ProblemApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "UNSUPPORTED_LANGUAGE", "当前只支持 C++ ACM 正式提交。");
        var problem = problems.findProblem(problemId);
        if (problem == null || problem.visibility() != Visibility.PUBLIC || problem.status() != ProblemStatus.ACTIVE
                || problem.currentPublishedVersionId() == null) throw unavailable();
        var version = problems.findVersion(problemId, problem.currentPublishedVersionId());
        if (version == null || version.status() != VersionStatus.PUBLISHED) throw unavailable();
        if (version.codeMode() != CodeMode.ACM || problems.findLanguages(version.id()).stream()
                .noneMatch(l -> languageId.equals(l.languageId()))) throw new ProblemApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_CODE_MODE", "当前题目不支持 C++ ACM 正式提交。");
        var testData = version.testDataVersionId() == null ? null : data.find(problemId, version.testDataVersionId());
        if (testData == null || testData.status() != com.cherryoj.problemservice.api.TestDataDtos.Status.READY
                || testData.caseCount() == null || testData.caseCount() < 1 || testData.caseCount() > 1000
                || testData.contentSha256() == null || !testData.contentSha256().matches("[a-f0-9]{64}")) throw unavailable();
        return new Snapshot(problem.id(), version.id(), version.versionNo(), version.title(), testData.id(),
                testData.contentSha256(), languageId, "ACM", testData.caseCount());
    }

    private static ProblemApiException unavailable() {
        return new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_AVAILABLE", "题目当前不可提交。");
    }

    public record Snapshot(String problemId, String problemVersionId, int problemVersionNo, String problemTitle,
                           String testDataVersionId, String testDataContentSha256, String languageId,
                           String codeMode, int totalCount) {}
}
