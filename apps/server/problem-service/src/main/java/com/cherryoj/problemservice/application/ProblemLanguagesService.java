package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.api.PublicProblemDtos;
import com.cherryoj.problemservice.integration.judging.JudgingClient;
import com.cherryoj.problemservice.integration.judging.JudgingDtos;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.storage.TestDataStore;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProblemLanguagesService {
    private final PublicProblemService publicProblems;
    private final AdminProblemMapper problems;
    private final TestDataStore data;
    private final JudgingClient judging;

    public ProblemLanguagesService(
            PublicProblemService publicProblems,
            AdminProblemMapper problems,
            TestDataStore data,
            JudgingClient judging) {
        this.publicProblems = publicProblems;
        this.problems = problems;
        this.data = data;
        this.judging = judging;
    }

    public PublicProblemDtos.ProblemLanguages ready(String slug, String traceparent) {
        var detail = publicProblems.detail(slug);
        var problem = problems.findProblem(detail.problemId());
        if (problem == null
                || problem.status() != com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus.ACTIVE
                || problem.visibility() != com.cherryoj.problemservice.api.AdminProblemDtos.Visibility.PUBLIC) {
            throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "公开题目不存在。");
        }
        if (problem.testDataLocation() == null) return new PublicProblemDtos.ProblemLanguages(List.of());
        TestDataStore.Info info;
        try {
            info = data.describe(problem.testDataLocation());
        } catch (TestDataStore.AssetException error) {
            return new PublicProblemDtos.ProblemLanguages(List.of());
        }
        var languages = problems.findLanguages(problem.id());
        if (languages.isEmpty()) return new PublicProblemDtos.ProblemLanguages(List.of());
        var ids = languages.stream().map(language -> language.languageId()).toList();
        var response = judging.problemReadiness(
                new JudgingDtos.ProblemReadinessRequest(problem.id(), info.digest(), info.maxTestcaseIoBytes(), ids),
                traceparent);
        if (response == null
                || response.languages() == null
                || !response.languages().stream()
                        .map(JudgingDtos.ProblemLanguageReadiness::languageId)
                        .toList()
                        .equals(ids)) {
            throw new ProblemApiException(HttpStatus.BAD_GATEWAY, "JUDGING_INVALID_RESPONSE", "判题服务响应格式无效。");
        }
        var ready = response.languages().stream()
                .filter(JudgingDtos.ProblemLanguageReadiness::ready)
                .map(JudgingDtos.ProblemLanguageReadiness::languageId)
                .collect(java.util.stream.Collectors.toSet());
        return new PublicProblemDtos.ProblemLanguages(languages.stream()
                .filter(language -> ready.contains(language.languageId()))
                .filter(language -> !"CORE".equals(detail.codeMode())
                        || language.judgeTemplate() != null
                                && !language.judgeTemplate().isBlank()
                                && problems.findLanguagePrefix(language.languageId()) != null)
                .map(language -> new PublicProblemDtos.ProblemLanguage(
                        language.languageId(), language.displayName(), language.starterCode()))
                .toList());
    }
}
