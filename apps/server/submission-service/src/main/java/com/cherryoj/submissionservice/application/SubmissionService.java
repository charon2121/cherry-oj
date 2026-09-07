package com.cherryoj.submissionservice.application;

import com.cherryoj.submissionservice.api.SubmissionDtos.*;
import com.cherryoj.submissionservice.api.SubmissionException;
import com.cherryoj.submissionservice.integration.SubmissionPrerequisites;
import com.cherryoj.submissionservice.persistence.SubmissionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class SubmissionService {
    private final SubmissionMapper store;
    private final SubmissionPrerequisites prerequisites;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final boolean accepting, messaging;
    public SubmissionService(SubmissionMapper store, SubmissionPrerequisites prerequisites, ObjectMapper json,
            PlatformTransactionManager manager, @Value("${cherry.submission.accepting:false}") boolean accepting,
            @Value("${cherry.submission.messaging-enabled:false}") boolean messaging) {
        this.store=store; this.prerequisites=prerequisites; this.json=json; this.transaction=new TransactionTemplate(manager);
        this.accepting=accepting; this.messaging=messaging;
    }
    public Created create(String user, String key, Create request) {
        UUID.fromString(key);
        if (!"cpp".equals(request.languageId())) throw problem(HttpStatus.UNPROCESSABLE_ENTITY,"UNSUPPORTED_LANGUAGE","当前只支持 C++。");
        if (request.source()==null || request.source().isBlank()) throw problem(HttpStatus.BAD_REQUEST,"INVALID_SOURCE","请输入完整代码。");
        if (request.source().getBytes(StandardCharsets.UTF_8).length>262144) throw problem(HttpStatus.PAYLOAD_TOO_LARGE,"SOURCE_TOO_LARGE","代码超过 256 KiB。");
        String digest=sha(json.writeValueAsString(request));
        var replay=replay(user,key,digest);
        if (replay!=null) return replay;
        if (!accepting || !messaging) throw problem(HttpStatus.SERVICE_UNAVAILABLE,"SUBMISSIONS_PAUSED","正式提交暂时不可用。");
        var snapshot=prerequisites.snapshot(request.problemId().toString(),request.languageId());
        validateSnapshot(snapshot, request);
        if (!request.expectedProblemVersionId().toString().equals(snapshot.problemVersionId())) {
            throw problem(HttpStatus.CONFLICT,"PROBLEM_VERSION_CHANGED","题目已更新，请刷新题面后重新确认。");
        }
        var profile=prerequisites.profile(snapshot);
        validateProfile(profile,snapshot);
        Instant now=Instant.now();
        String id=uuid7(), event=uuid7();
        var view=new View(id,snapshot.problemId(),snapshot.problemVersionId(),snapshot.problemVersionNo(),snapshot.problemTitle(),
                request.languageId(),"PENDING",now,null,null,null,null,null,null,null,null);
        var input=new Input(id,"2",snapshot.problemId(),snapshot.problemVersionId(),snapshot.testDataVersionId(),request.languageId(),
                request.source(),sha(request.source()),profile.judgeEnvironmentId(),profile.environmentFingerprint(),profile.languageCalibrationId(),
                profile.effectiveLimits(),now,snapshot.testDataContentSha256(),snapshot.totalCount(),profile.executionBudgetNs());
        String payload=json.writeValueAsString(Map.of("eventId",event,"eventType","JudgeRequested","eventVersion",1,
                "occurredAt",now.toString(),"traceId",traceId(),"aggregateId",id,
                "payload",Map.of("submissionId",id,"judgeInputContractVersion","2")));
        try {
            return transaction.execute(status -> {
                // 唯一键冲突会回滚整个事务；不能在这个事务里吞异常后继续提交半条事实。
                var time=LocalDateTime.ofInstant(now,ZoneOffset.UTC);
                store.put(id,user,snapshot.problemId(),json.writeValueAsString(view),request.source(),time);
                store.putInput(id,json.writeValueAsString(input));
                store.putRequest(user,key,digest,id,time);
                store.putOutbox(event,id,payload,traceParent(),time);
                return new Created(view,true);
            });
        } catch (DuplicateKeyException conflict) {
            var existing=replay(user,key,digest);
            if (existing!=null) return existing;
            throw problem(HttpStatus.CONFLICT,"SUBMISSION_CONFLICT","提交请求发生冲突，请确认原请求。");
        }
    }
    private Created replay(String user,String key,String digest) {
        var request=store.request(user,key);
        if (request==null) return null;
        if (!request.requestDigest().equals(digest)) throw problem(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","该请求编号已用于不同的提交内容。");
        return new Created(get(user,request.submissionId()),false);
    }
    public View get(String user,String id) {
        var row=store.get(id);
        if (row==null || !row.userId().equals(user)) throw missing();
        return json.readValue(row.readModel(),View.class);
    }
    public View request(String user,String key) {
        var request=store.request(user,key); if (request==null) throw missing();
        return get(user,request.submissionId());
    }
    public Input input(String id) {
        String value=store.input(id); if (value==null) throw missing();
        return json.readValue(value,Input.class);
    }
    private static void validateSnapshot(Snapshot s,Create r) {
        if (s==null || !r.problemId().toString().equals(s.problemId()) || !"cpp".equals(s.languageId()) || !"ACM".equals(s.codeMode())
                || s.problemVersionId()==null || s.testDataVersionId()==null || s.problemVersionNo()<1 || s.problemTitle()==null
                || s.testDataContentSha256()==null || !s.testDataContentSha256().matches("[a-f0-9]{64}") || s.totalCount()<1 || s.totalCount()>1000) throw invalidSnapshot();
        try { UUID.fromString(s.problemVersionId()); UUID.fromString(s.testDataVersionId()); } catch (IllegalArgumentException error) { throw invalidSnapshot(); }
    }
    private static void validateProfile(Profile p,Snapshot s) {
        if (p==null || !s.problemVersionId().equals(p.problemVersionId()) || !s.testDataVersionId().equals(p.testDataVersionId())
                || !s.languageId().equals(p.languageId()) || p.judgeEnvironmentId()==null || p.languageCalibrationId()==null
                || p.environmentFingerprint()==null || p.environmentFingerprint().isBlank() || p.effectiveLimits()==null) throw invalidSnapshot();
        var limits=p.effectiveLimits();
        if (p.executionBudgetNs()<=0 || limits.cpuNs()<=0 || limits.memoryBytes()<=0 || (limits.clockNs()!=null && limits.clockNs()<=0)) throw invalidSnapshot();
        try { UUID.fromString(p.judgeEnvironmentId()); UUID.fromString(p.languageCalibrationId()); } catch (IllegalArgumentException error) { throw invalidSnapshot(); }
    }
    private static SubmissionException invalidSnapshot() { return problem(HttpStatus.SERVICE_UNAVAILABLE,"INVALID_JUDGE_SNAPSHOT","当前题目暂时无法判题。"); }
    private static SubmissionException missing() { return problem(HttpStatus.NOT_FOUND,"SUBMISSION_NOT_FOUND","记录不存在或无权查看。"); }
    public static SubmissionException problem(HttpStatus status,String code,String message) { return new SubmissionException(status,code,message); }
    public static String sha(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    public static String uuid7() {
        var random=UUID.randomUUID();
        return new UUID((System.currentTimeMillis()<<16)|0x7000L|(random.getMostSignificantBits()&0xfffL),
                (random.getLeastSignificantBits()&0x3fffffffffffffffL)|0x8000000000000000L).toString();
    }
    private static String traceParent() {
        String trace=org.slf4j.MDC.get("traceId"), span=org.slf4j.MDC.get("spanId");
        return trace!=null && trace.matches("[a-f0-9]{32}") && !trace.equals("0".repeat(32))
                && span!=null && span.matches("[a-f0-9]{16}") && !span.equals("0".repeat(16))
                ? "00-"+trace+"-"+span+"-01" : null;
    }
    private static String traceId() {
        String trace=org.slf4j.MDC.get("traceId");
        return trace!=null && trace.matches("[a-f0-9]{32}") ? trace : UUID.randomUUID().toString().replace("-","");
    }
}
