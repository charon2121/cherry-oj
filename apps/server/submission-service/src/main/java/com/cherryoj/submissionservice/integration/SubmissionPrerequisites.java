package com.cherryoj.submissionservice.integration;

import com.cherryoj.submissionservice.judging.ExecutionProfile;
import com.cherryoj.submissionservice.judging.ProblemJudgeSnapshot;

public interface SubmissionPrerequisites {
    ProblemJudgeSnapshot resolveProblemJudgeSnapshot(String problemId, String languageId);

    ExecutionProfile resolveExecutionProfile(ProblemJudgeSnapshot snapshot);
}
