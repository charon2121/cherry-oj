package com.cherryoj.problemservice.persistence;

import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguageRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.SampleRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.VersionRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminProblemMapper {

    long countProblems(@Param("q") String q, @Param("status") ProblemStatus status);

    List<ProblemRow> listProblems(
            @Param("q") String q,
            @Param("status") ProblemStatus status,
            @Param("offset") long offset,
            @Param("limit") int limit);

    ProblemRow findProblem(@Param("id") String id);

    ProblemRow findProblemForUpdate(@Param("id") String id);

    List<VersionRow> findVersions(@Param("problemId") String problemId);

    List<VersionRow> findVersionsForProblems(@Param("problemIds") List<String> problemIds);

    VersionRow findVersion(@Param("problemId") String problemId, @Param("versionId") String versionId);

    VersionRow findVersionForUpdate(@Param("problemId") String problemId, @Param("versionId") String versionId);

    List<SampleRow> findSamples(@Param("versionId") String versionId);

    List<LanguageRow> findLanguages(@Param("versionId") String versionId);

    int maxVersionNo(@Param("problemId") String problemId);

    int countDrafts(@Param("problemId") String problemId);

    int insertProblem(@Param("problem") ProblemRow problem, @Param("createdBy") String createdBy);

    int insertVersion(@Param("version") VersionRow version, @Param("createdBy") String createdBy);

    int insertSample(
            @Param("id") String id,
            @Param("versionId") String versionId,
            @Param("ordinal") int ordinal,
            @Param("inputText") String inputText,
            @Param("expectedOutputText") String expectedOutputText,
            @Param("explanationMarkdown") String explanationMarkdown);

    int insertLanguage(
            @Param("versionId") String versionId,
            @Param("languageId") String languageId,
            @Param("displayOrder") int displayOrder,
            @Param("starterCode") String starterCode);

    int updateProblem(
            @Param("id") String id,
            @Param("slug") String slug,
            @Param("visibility") Visibility visibility,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("expectedRowVersion") long expectedRowVersion);

    int archiveProblem(
            @Param("id") String id,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("expectedRowVersion") long expectedRowVersion);

    int touchProblem(
            @Param("id") String id,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("expectedRowVersion") long expectedRowVersion);

    int updateDraft(
            @Param("version") VersionRow version,
            @Param("expectedRowVersion") long expectedRowVersion);

    int deleteSamples(@Param("versionId") String versionId);

    int deleteLanguages(@Param("versionId") String versionId);

    int deleteDraft(@Param("problemId") String problemId, @Param("versionId") String versionId);

    int beginValidation(
            @Param("problemId") String problemId,
            @Param("versionId") String versionId,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("expectedRowVersion") long expectedRowVersion);

    int finishValidation(
            @Param("problemId") String problemId,
            @Param("versionId") String versionId,
            @Param("targetStatus") com.cherryoj.problemservice.api.AdminProblemDtos.VersionStatus targetStatus,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("expectedRowVersion") long expectedRowVersion);

    int recoverStaleValidations(@Param("cutoff") LocalDateTime cutoff, @Param("updatedAt") LocalDateTime updatedAt);

    int publishVersion(
            @Param("problemId") String problemId,
            @Param("versionId") String versionId,
            @Param("actorUserId") String actorUserId,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("expectedRowVersion") long expectedRowVersion);

    int pointToPublishedVersion(
            @Param("problemId") String problemId,
            @Param("versionId") String versionId,
            @Param("updatedAt") LocalDateTime updatedAt);

    int insertAudit(
            @Param("id") String id,
            @Param("problemId") String problemId,
            @Param("problemVersionId") String problemVersionId,
            @Param("actorUserId") String actorUserId,
            @Param("action") String action,
            @Param("traceId") String traceId,
            @Param("detailJson") String detailJson,
            @Param("createdAt") LocalDateTime createdAt);
}
