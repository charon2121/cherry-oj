package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.domain.UuidV7;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FormalTaskStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;
    public FormalTaskStore(JdbcTemplate jdbc,PlatformTransactionManager manager,UuidV7 ids,Clock clock,ObjectMapper json) {
        this.jdbc=jdbc; transaction=new TransactionTemplate(manager); this.ids=ids; this.clock=clock; this.json=json;
    }
    public record Task(String id,String submissionId,int attemptNo,String leaseToken,LocalDateTime createdAt,String traceId,String traceParent) {}
    public record Outbox(String id,String key,String payload,String traceParent) {}
    public void receive(String eventId,String submissionId,Instant occurredAt,String traceId,String traceParent) {
        transaction.executeWithoutResult(status -> {
            if(jdbc.update("INSERT IGNORE INTO inbox_event VALUES(?,?)",eventId,now())==0) return;
            jdbc.update("INSERT IGNORE INTO judge_task(id,submission_id,status,next_attempt_at,created_at,trace_id,trace_parent) VALUES(?,?,'READY',?,?,?,?)",
                    ids.next().toString(),submissionId,now(),LocalDateTime.ofInstant(occurredAt,ZoneOffset.UTC),traceId,traceParent);
        });
    }
    public Task claim(Duration lease) {
        return transaction.execute(status -> {
            var rows=jdbc.query("""
                    SELECT id,submission_id,attempt_no,lease_token,created_at,trace_id,trace_parent
                    FROM judge_task WHERE status IN ('READY','RETRY_WAITING','RUNNING')
                    AND next_attempt_at<=? AND (lease_until IS NULL OR lease_until<=?)
                    ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, (rs,n)->new Task(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getString(4),rs.getObject(5,LocalDateTime.class),rs.getString(6),rs.getString(7)),now(),now());
            if(rows.isEmpty()) return null;
            Task before=rows.getFirst(); String token=ids.next().toString(); int attempt=before.attemptNo()+1;
            if(before.leaseToken()!=null) jdbc.update("UPDATE judge_attempt SET status='ABANDONED',error_code='LEASE_EXPIRED',finished_at=? WHERE task_id=? AND attempt_no=? AND status='RUNNING'",now(),before.id(),before.attemptNo());
            jdbc.update("UPDATE judge_task SET status='RUNNING',attempt_no=?,lease_token=?,lease_until=? WHERE id=?",attempt,token,now().plus(lease),before.id());
            jdbc.update("INSERT INTO judge_attempt(task_id,attempt_no,lease_token,status,started_at) VALUES(?,?,?,'RUNNING',?)",before.id(),attempt,token,now());
            var task=new Task(before.id(),before.submissionId(),attempt,token,before.createdAt(),before.traceId(),before.traceParent());
            emit(task,"JudgeStarted",Map.of("startedAt",clock.instant().toString()));
            return task;
        });
    }
    public boolean renew(Task task,Duration lease) {
        return jdbc.update("UPDATE judge_task SET lease_until=? WHERE id=? AND lease_token=? AND status='RUNNING' AND lease_until>?",now().plus(lease),task.id(),task.leaseToken(),now())==1;
    }
    public boolean finish(Task task,Map<String,Object> result,String errorCode,Duration retry) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            var rows=jdbc.queryForList("SELECT id FROM judge_task WHERE id=? AND lease_token=? AND status='RUNNING' AND lease_until>? FOR UPDATE",String.class,task.id(),task.leaseToken(),now());
            if(rows.isEmpty()) return false;
            String state=retry==null?"DONE":"RETRY_WAITING";
            jdbc.update("UPDATE judge_task SET status=?,lease_token=NULL,lease_until=NULL,next_attempt_at=? WHERE id=?",state,retry==null?now():now().plus(retry),task.id());
            jdbc.update("UPDATE judge_attempt SET status=?,error_code=?,finished_at=? WHERE task_id=? AND attempt_no=? AND lease_token=?",
                    result!=null?"COMPLETED":"FAILED",errorCode,now(),task.id(),task.attemptNo(),task.leaseToken());
            if(retry==null) {
                if(result!=null) emit(task,"JudgeCompleted",Map.of("finishedAt",clock.instant().toString(),"result",result));
                else emit(task,"JudgeFailed",Map.of("finishedAt",clock.instant().toString(),"errorCode",errorCode,"message","平台判题暂时失败。"));
            }
            return true;
        }));
    }
    private void emit(Task task,String type,Map<String,Object> extra) {
        String id=ids.next().toString();
        var payload=new LinkedHashMap<String,Object>(Map.of("submissionId",task.submissionId(),"taskId",task.id(),"attemptNo",task.attemptNo()));
        payload.putAll(extra);
        String body=json.writeValueAsString(Map.of("eventId",id,"eventType",type,"eventVersion",1,"occurredAt",clock.instant().toString(),
                "traceId",task.traceId(),"aggregateId",task.submissionId(),"payload",payload));
        if(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1048576) throw new IllegalArgumentException("lifecycle exceeds limit");
        jdbc.update("INSERT INTO outbox_event(event_id,message_key,payload,trace_parent,next_attempt_at,created_at) VALUES(?,?,?,?,?,?)",id,task.submissionId(),body,task.traceParent(),now(),now());
    }
    public List<Outbox> pending() {
        return jdbc.query("SELECT event_id,message_key,payload,trace_parent FROM outbox_event WHERE published=FALSE AND next_attempt_at<=? ORDER BY created_at LIMIT 50",
                (rs,n)->new Outbox(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),now());
    }
    public void published(String id) { jdbc.update("UPDATE outbox_event SET published=TRUE WHERE event_id=?",id); }
    public void retryPublish(String id) { jdbc.update("UPDATE outbox_event SET attempts=attempts+1,next_attempt_at=? WHERE event_id=?",now().plusSeconds(5),id); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
}
