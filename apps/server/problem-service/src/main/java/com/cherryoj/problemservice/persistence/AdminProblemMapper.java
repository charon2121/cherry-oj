package com.cherryoj.problemservice.persistence;

import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguagePrefixRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguageRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.SampleRow;
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

    List<SampleRow> findSamples(@Param("problemId") String problemId);

    List<LanguageRow> findLanguages(@Param("problemId") String problemId);

    LanguagePrefixRow findLanguagePrefix(@Param("languageId") String languageId);

    List<LanguagePrefixRow> findLanguageSettings();

    /** 以读到的 updated_at 作为并发前提；返回 0 表示语言不存在或已被别人改过。 */
    int updateLanguagePrefix(
            @Param("languageId") String languageId,
            @Param("solutionPrefix") String solutionPrefix,
            @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    int insertProblem(@Param("problem") ProblemRow problem, @Param("createdBy") String createdBy);

    int insertSample(
            @Param("id") String id,
            @Param("problemId") String problemId,
            @Param("ordinal") int ordinal,
            @Param("inputText") String inputText,
            @Param("expectedOutputText") String expectedOutputText,
            @Param("explanationMarkdown") String explanationMarkdown);

    int insertLanguage(
            @Param("problemId") String problemId,
            @Param("languageId") String languageId,
            @Param("displayOrder") int displayOrder,
            @Param("starterCode") String starterCode);

    /** 原地改写起始代码与驱动代码；驱动代码没变时调用方传回原来的修改时间，所以不能用删了重插。 */
    int updateLanguage(
            @Param("problemId") String problemId,
            @Param("languageId") String languageId,
            @Param("starterCode") String starterCode,
            @Param("judgeTemplate") String judgeTemplate,
            @Param("judgeTemplateUpdatedAt") LocalDateTime judgeTemplateUpdatedAt);

    /** 改写题面、样例之外的全部内容列与 slug；可见性和测试数据地址不在这里改。 */
    int updateContent(@Param("problem") ProblemRow problem, @Param("expectedRowVersion") long expectedRowVersion);

    int archiveProblem(
            @Param("id") String id,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("expectedRowVersion") long expectedRowVersion);

    /** 第一次公开时记下 published_at，之后保持不变。 */
    int publishProblem(
            @Param("id") String id,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("expectedRowVersion") long expectedRowVersion);

    int unpublishProblem(
            @Param("id") String id,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("expectedRowVersion") long expectedRowVersion);

    /** 测试数据另有自己的指纹，不改乐观锁计数，免得上传数据让正在编辑题面的人白白冲突。 */
    int updateTestData(
            @Param("id") String id,
            @Param("location") String location,
            @Param("updatedAt") LocalDateTime updatedAt);

    int deleteSamples(@Param("problemId") String problemId);

    int deleteLanguages(@Param("problemId") String problemId);

    int deleteAudits(@Param("problemId") String problemId);

    /** 只删从未公开过的题目：它不可能有提交。 */
    int deleteNeverPublished(@Param("id") String id, @Param("expectedRowVersion") long expectedRowVersion);

    int insertAudit(
            @Param("id") String id,
            @Param("problemId") String problemId,
            @Param("actorUserId") String actorUserId,
            @Param("action") String action,
            @Param("traceId") String traceId,
            @Param("detailJson") String detailJson,
            @Param("createdAt") LocalDateTime createdAt);
}
