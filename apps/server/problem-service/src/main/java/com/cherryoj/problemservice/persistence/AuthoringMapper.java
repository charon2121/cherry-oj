package com.cherryoj.problemservice.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuthoringMapper {
    String SUMMARY = """
            BIN_TO_UUID(id) AS id, kind, status, stage, row_version, attempt, created_at, updated_at, applied_at
            """;
    String JOB = """
            BIN_TO_UUID(id) AS id, BIN_TO_UUID(problem_id) AS problem_id, kind, status, stage, row_version, attempt,
            created_at, updated_at, applied_at, snapshot_digest, input_json, result_json, error_code, applied_json,
            report IS NOT NULL AS report_available
            """;

    record Summary(
            String id,
            String kind,
            String status,
            String stage,
            long rowVersion,
            int attempt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime appliedAt) {}

    record Job(
            String id,
            String problemId,
            String kind,
            String status,
            String stage,
            long rowVersion,
            int attempt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime appliedAt,
            String snapshotDigest,
            String inputJson,
            String resultJson,
            String errorCode,
            String appliedJson,
            boolean reportAvailable) {
        public Summary summary() {
            return new Summary(id, kind, status, stage, rowVersion, attempt, createdAt, updatedAt, appliedAt);
        }
    }

    record Lease(int slotId, String jobId, Integer attempt, LocalDateTime leaseUntil) {}

    record Artifact(byte[] archive) {}

    @Select("""
            SELECT id AS slot_id, BIN_TO_UUID(job_id) AS job_id, attempt, lease_until
            FROM problem_authoring_queue WHERE id=1 FOR UPDATE
            """)
    Lease lockQueue();

    @Select("SELECT COUNT(*) FROM problem_authoring_job WHERE status='QUEUED'")
    int queuedCount();

    @Select("SELECT " + SUMMARY + """
            FROM problem_authoring_job WHERE problem_id=UUID_TO_BIN(#{problemId})
            ORDER BY created_at DESC, id DESC LIMIT 50
            """)
    List<Summary> list(@Param("problemId") String problemId);

    @Select("SELECT " + JOB
            + "FROM problem_authoring_job WHERE id=UUID_TO_BIN(#{id}) AND problem_id=UUID_TO_BIN(#{problemId})")
    Job get(@Param("problemId") String problemId, @Param("id") String id);

    @Select(
            "SELECT " + JOB
                    + "FROM problem_authoring_job WHERE id=UUID_TO_BIN(#{id}) AND problem_id=UUID_TO_BIN(#{problemId}) FOR UPDATE")
    Job lock(@Param("problemId") String problemId, @Param("id") String id);

    @Select(
            "SELECT BIN_TO_UUID(id) FROM problem_authoring_job WHERE problem_id=UUID_TO_BIN(#{problemId}) AND request_digest=#{digest}")
    String existing(@Param("problemId") String problemId, @Param("digest") String digest);

    @Select("SELECT " + JOB + """
            FROM problem_authoring_job WHERE status='QUEUED' ORDER BY updated_at, id LIMIT 1 FOR UPDATE
            """)
    Job next();

    @Insert("""
            INSERT INTO problem_authoring_job
                (id, problem_id, request_digest, snapshot_digest, row_version, kind, status, stage, attempt, input_json, created_at, updated_at)
            VALUES (UUID_TO_BIN(#{id}), UUID_TO_BIN(#{problemId}), #{digest}, #{snapshot}, #{rowVersion},
                    #{kind}, 'QUEUED', 'QUEUED', 1, #{input}, #{now}, #{now})
            """)
    void insert(
            @Param("id") String id,
            @Param("problemId") String problemId,
            @Param("digest") String digest,
            @Param("snapshot") String snapshot,
            @Param("rowVersion") long rowVersion,
            @Param("kind") String kind,
            @Param("input") String input,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE problem_authoring_job SET status='RUNNING', stage=#{stage}, updated_at=#{now}
            WHERE id=UUID_TO_BIN(#{id}) AND attempt=#{attempt} AND status='QUEUED'
            """)
    int start(
            @Param("id") String id,
            @Param("attempt") int attempt,
            @Param("stage") String stage,
            @Param("now") LocalDateTime now);

    @Update(
            "UPDATE problem_authoring_queue SET job_id=UUID_TO_BIN(#{id}), attempt=#{attempt}, lease_until=#{until} WHERE id=1")
    void lease(@Param("id") String id, @Param("attempt") int attempt, @Param("until") LocalDateTime until);

    @Update("""
            UPDATE problem_authoring_queue SET job_id=NULL, attempt=NULL, lease_until=NULL
            WHERE id=1 AND job_id=UUID_TO_BIN(#{jobId}) AND attempt=#{attempt}
            """)
    void release(@Param("jobId") String jobId, @Param("attempt") int attempt);

    @Select("""
            SELECT EXISTS(SELECT 1 FROM problem_authoring_job
            WHERE id=UUID_TO_BIN(#{id}) AND attempt=#{attempt} AND status='RUNNING')
            """)
    boolean running(@Param("id") String id, @Param("attempt") int attempt);

    @Update("""
            UPDATE problem_authoring_job SET stage=#{stage}, updated_at=#{now}
            WHERE id=UUID_TO_BIN(#{id}) AND attempt=#{attempt} AND status='RUNNING' AND stage<>#{stage}
            """)
    void progress(
            @Param("id") String id,
            @Param("attempt") int attempt,
            @Param("stage") String stage,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE problem_authoring_job
            SET status=#{status}, stage=COALESCE(#{stage}, stage), result_json=#{result}, error_code=#{error},
                report=#{report}, archive=#{archive}, updated_at=#{now}
            WHERE id=UUID_TO_BIN(#{id}) AND attempt=#{attempt} AND status='RUNNING'
            """)
    void finish(
            @Param("id") String id,
            @Param("attempt") int attempt,
            @Param("status") String status,
            @Param("stage") String stage,
            @Param("result") String result,
            @Param("error") String error,
            @Param("report") String report,
            @Param("archive") byte[] archive,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE problem_authoring_job SET status='CANCELLED', updated_at=#{now}
            WHERE id=UUID_TO_BIN(#{id}) AND status IN ('QUEUED', 'RUNNING')
            """)
    int cancel(@Param("id") String id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE problem_authoring_job
            SET status='QUEUED', stage='QUEUED', attempt=attempt+1, result_json=NULL, error_code=NULL, report=NULL,
                archive=NULL, updated_at=#{now}
            WHERE id=UUID_TO_BIN(#{id}) AND status IN ('FAILED', 'CANCELLED') AND attempt<5
            """)
    int retry(@Param("id") String id, @Param("now") LocalDateTime now);

    @Select(
            "SELECT report FROM problem_authoring_job WHERE id=UUID_TO_BIN(#{id}) AND problem_id=UUID_TO_BIN(#{problemId})")
    String report(@Param("problemId") String problemId, @Param("id") String id);

    @Select(
            "SELECT archive FROM problem_authoring_job WHERE id=UUID_TO_BIN(#{id}) AND problem_id=UUID_TO_BIN(#{problemId})")
    Artifact archive(@Param("problemId") String problemId, @Param("id") String id);

    @Update("""
            UPDATE problem_authoring_job SET applied_at=#{now}, applied_json=#{result}, archive=NULL, updated_at=#{now}
            WHERE id=UUID_TO_BIN(#{id}) AND status='SUCCEEDED' AND applied_at IS NULL
            """)
    int applied(@Param("id") String id, @Param("result") String result, @Param("now") LocalDateTime now);
}
