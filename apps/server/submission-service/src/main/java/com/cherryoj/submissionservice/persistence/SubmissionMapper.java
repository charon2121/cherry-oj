package com.cherryoj.submissionservice.persistence;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SubmissionMapper {
    record Row(String id, String userId, String status, String readModel, String taskId, int attemptNo, long rowVersion) {}
    record SourceRow(String id, String problemId, String readModel, String source) {
        @Override public String toString() { return "SourceRow[id=" + id + ", source=<redacted>]"; }
    }
    // Query only the owner's selected problem; source is never projected into a history page.
    String HISTORY_WHERE = " FROM submission WHERE user_id=#{user} AND problem_id=#{problem} "
            + "AND (#{verdict} IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(read_model,'$.verdict'))=#{verdict}) ";
    @Select("SELECT read_model" + HISTORY_WHERE + "ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<String> history(@Param("user") String user, @Param("problem") String problem,
                         @Param("verdict") String verdict, @Param("size") int size, @Param("offset") long offset);
    @Select("SELECT COUNT(*)" + HISTORY_WHERE)
    long historyCount(@Param("user") String user, @Param("problem") String problem, @Param("verdict") String verdict);
    @Select("SELECT id,problem_id,read_model,source FROM submission WHERE id=#{id} AND user_id=#{user}")
    SourceRow source(@Param("user") String user, @Param("id") String id);
    record RequestRow(String requestDigest, String submissionId) {}
    record Outbox(String eventId, String messageKey, String payload, String traceParent) {}
    @Select("SELECT id,user_id,status,read_model,task_id,attempt_no,row_version FROM submission WHERE id=#{id}")
    Row get(String id);
    @Select("SELECT id,user_id,status,read_model,task_id,attempt_no,row_version FROM submission WHERE id=#{id} FOR UPDATE")
    Row lock(String id);
    @Select("SELECT request_digest,submission_id FROM submission_request WHERE user_id=#{user} AND idempotency_key=#{key}")
    RequestRow request(@Param("user") String user, @Param("key") String key);
    @Select("SELECT payload FROM judge_input WHERE submission_id=#{id}") String input(String id);
    @Insert("INSERT INTO submission(id,user_id,problem_id,status,read_model,source,created_at) VALUES(#{id},#{user},#{problem},'PENDING',#{model},#{source},#{now})")
    void put(@Param("id") String id,@Param("user") String user,@Param("problem") String problem,@Param("model") String model,@Param("source") String source,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO judge_input VALUES(#{id},#{payload})") void putInput(@Param("id") String id,@Param("payload") String payload);
    @Insert("INSERT INTO submission_request VALUES(#{user},#{key},#{digest},#{id},#{now})")
    void putRequest(@Param("user") String user,@Param("key") String key,@Param("digest") String digest,@Param("id") String id,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO outbox_event(event_id,message_key,payload,trace_parent,next_attempt_at,created_at) VALUES(#{event},#{id},#{payload},#{traceParent},#{now},#{now})")
    void putOutbox(@Param("event") String event,@Param("id") String id,@Param("payload") String payload,@Param("traceParent") String traceParent,@Param("now") LocalDateTime now);
    @Select("SELECT event_id,message_key,payload,trace_parent FROM outbox_event WHERE published=FALSE AND next_attempt_at<=UTC_TIMESTAMP(6) ORDER BY created_at LIMIT 50") List<Outbox> pending();
    @Update("UPDATE outbox_event SET published=TRUE WHERE event_id=#{id}") void published(String id);
    @Update("UPDATE outbox_event SET attempts=attempts+1,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 5 SECOND) WHERE event_id=#{id}") void retry(String id);
    @Insert("INSERT IGNORE INTO inbox_event VALUES(#{id},UTC_TIMESTAMP(6))") int inbox(String id);
    @Update("UPDATE submission SET status=#{status},read_model=#{model},task_id=#{task},attempt_no=#{attempt},row_version=row_version+1 WHERE id=#{id} AND row_version=#{version}")
    int update(@Param("id") String id,@Param("status") String status,@Param("model") String model,@Param("task") String task,@Param("attempt") int attempt,@Param("version") long version);
}
