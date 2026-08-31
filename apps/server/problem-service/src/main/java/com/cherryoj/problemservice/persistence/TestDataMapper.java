package com.cherryoj.problemservice.persistence;

import com.cherryoj.problemservice.persistence.TestDataRows.TestDataRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TestDataMapper {

    List<TestDataRow> list(@Param("problemId") String problemId);

    TestDataRow find(@Param("problemId") String problemId, @Param("id") String id);

    TestDataRow findReadyByHash(@Param("problemId") String problemId, @Param("sha256") String sha256);

    int insertUploading(
            @Param("id") String id,
            @Param("problemId") String problemId,
            @Param("storageRef") String storageRef,
            @Param("createdBy") String createdBy,
            @Param("createdAt") LocalDateTime createdAt);

    int markReady(
            @Param("id") String id,
            @Param("sha256") String sha256,
            @Param("caseCount") int caseCount,
            @Param("totalBytes") long totalBytes,
            @Param("manifestJson") String manifestJson,
            @Param("readyAt") LocalDateTime readyAt);

    int markFailed(@Param("id") String id, @Param("errorMessage") String errorMessage);

    int deleteUploading(@Param("id") String id);

    int bindDraft(
            @Param("problemId") String problemId,
            @Param("versionId") String versionId,
            @Param("testDataVersionId") String testDataVersionId,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("expectedRowVersion") long expectedRowVersion);

    List<String> listReadyStorageRefs();

    int failStaleUploads(@Param("cutoff") LocalDateTime cutoff, @Param("errorMessage") String errorMessage);
}
