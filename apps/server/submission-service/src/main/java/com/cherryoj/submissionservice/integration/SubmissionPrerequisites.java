package com.cherryoj.submissionservice.integration;

import com.cherryoj.submissionservice.api.SubmissionDtos.*;
public interface SubmissionPrerequisites {
    Snapshot snapshot(String problemId, String languageId);
    Profile profile(Snapshot snapshot);
}
