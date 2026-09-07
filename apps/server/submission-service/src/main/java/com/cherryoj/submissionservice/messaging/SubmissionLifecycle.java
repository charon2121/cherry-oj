package com.cherryoj.submissionservice.messaging;

import com.cherryoj.submissionservice.api.SubmissionDtos.*;
import com.cherryoj.submissionservice.persistence.SubmissionMapper;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SubmissionLifecycle {
    private static final Set<String> VERDICTS=Set.of("AC","WA","PE","CE","RE","TLE","MLE","OLE","SE");
    private final SubmissionMapper store;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    public SubmissionLifecycle(SubmissionMapper store,ObjectMapper json,PlatformTransactionManager manager) {
        this.store=store; this.json=json; transaction=new TransactionTemplate(manager);
    }
    public void apply(String key,String raw) {
        if (raw==null || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1048576) throw invalid();
        JsonNode event=json.readTree(raw);
        fields(event,Set.of("eventId","eventType","eventVersion","occurredAt","traceId","aggregateId","payload"));
        String id=text(event,"aggregateId"), eventId=text(event,"eventId"), type=text(event,"eventType");
        UUID.fromString(id); UUID.fromString(eventId);
        if (!id.equals(key) || (!event.path("eventVersion").isIntegralNumber() || event.path("eventVersion").asInt()!=1) || !text(event,"traceId").matches("[0-9a-f]{32}")) throw invalid();
        Instant.parse(text(event,"occurredAt"));
        var payload=event.path("payload");
        var allowed=switch(type) {
            case "JudgeStarted" -> Set.of("submissionId","taskId","attemptNo","startedAt");
            case "JudgeCompleted" -> Set.of("submissionId","taskId","attemptNo","finishedAt","result");
            case "JudgeFailed" -> Set.of("submissionId","taskId","attemptNo","finishedAt","errorCode","message");
            default -> throw invalid();
        };
        fields(payload,allowed);
        if (!id.equals(text(payload,"submissionId"))) throw invalid();
        String task=text(payload,"taskId"); UUID.fromString(task);
        if (!payload.path("attemptNo").isIntegralNumber() || !payload.path("attemptNo").canConvertToInt()) throw invalid();
        int attempt=payload.path("attemptNo").asInt(); if(attempt<1) throw invalid();
        var result=payload.path("result");
        if (type.equals("JudgeCompleted")) validateResult(result);
        Instant finished=type.equals("JudgeStarted") ? null : Instant.parse(text(payload,"finishedAt"));
        if (type.equals("JudgeStarted")) Instant.parse(text(payload,"startedAt"));
        transaction.executeWithoutResult(status -> {
            var row=store.lock(id); if (row==null) throw invalid();
            if (store.inbox(eventId)==0 || row.status().equals("DONE")) return;
            if ((row.taskId()!=null && !row.taskId().equals(task)) || row.attemptNo()>attempt) return;
            View before=json.readValue(row.readModel(),View.class);
            String verdict=null,message=null; Long cpu=null,memory=null; Integer passed=null,executed=null,total=null;
            if (type.equals("JudgeCompleted")) {
                var input=json.readValue(store.input(id),Input.class);
                if (!input.environmentFingerprint().equals(text(result,"environmentFingerprint"))) throw invalid();
                verdict=text(result,"verdict"); cpu=number(result,"cpuNs"); memory=number(result,"memoryBytes");
                if (result.has("executedCount")) {
                    executed=Math.toIntExact(number(result,"executedCount")); passed=Math.toIntExact(number(result,"passedCount"));
                    total=input.totalCount();
                    if (passed>executed || executed>total || (result.has("totalCount") && number(result,"totalCount")!=total.longValue())) throw invalid();
                }
                if ("CE".equals(verdict) && result.has("message")) message=diagnostic(text(result,"message"));
                if ("SE".equals(verdict)) message="平台判题暂时失败，请稍后重新提交。";
            } else if (type.equals("JudgeFailed")) { verdict="SE"; message="平台判题暂时失败，请稍后重新提交。"; }
            String next=finished==null?"JUDGING":"DONE";
            var view=new View(before.id(),before.problemId(),before.problemVersionId(),before.problemVersionNo(),before.problemTitle(),
                    before.languageId(),next,before.createdAt(),verdict,cpu,memory,passed,executed,total,message,finished);
            if (store.update(id,next,json.writeValueAsString(view),task,attempt,row.rowVersion())!=1) throw new IllegalStateException("submission state conflict");
        });
    }
    private static void validateResult(JsonNode result) {
        fields(result,Set.of("verdict","environmentFingerprint","cpuNs","memoryBytes","passedCount","executedCount","totalCount","message"));
        if (!VERDICTS.contains(text(result,"verdict"))) throw invalid();
        text(result,"environmentFingerprint");
        for (String field:ListHolder.NUMBERS) if(result.has(field)) number(result,field);
        for (String field:Set.of("passedCount","executedCount","totalCount")) if(result.has(field) && number(result,field)>1000) throw invalid();
        if (result.has("message") && text(result,"message").length()>8192) throw invalid();
        if (result.has("passedCount")!=result.has("executedCount")) throw invalid();
        if (result.has("message") && (!"CE".equals(text(result,"verdict")) && !"SE".equals(text(result,"verdict")))) throw invalid();
    }
    private static class ListHolder { static final Set<String> NUMBERS=Set.of("cpuNs","memoryBytes","passedCount","executedCount","totalCount"); }
    private static Long number(JsonNode node,String field) {
        var value=node.path(field); if(value.isMissingNode()) return null;
        if(!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong()<0) throw invalid(); return value.asLong();
    }
    private static void fields(JsonNode node,Set<String> allowed) {
        if(!node.isObject()) throw invalid();
        for(var property:node.properties()) if(!allowed.contains(property.getKey())) throw invalid();
    }
    private static String text(JsonNode node,String field) {
        if(!node.path(field).isString() || node.path(field).asString().isBlank()) throw invalid(); return node.path(field).asString();
    }
    public static String diagnostic(String raw) {
        String safe=raw.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "").replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .replaceAll("(?:[A-Za-z]:)?[/\\\\](?:[^\\s:]+[/\\\\])*[^\\s:]+", "<file>");
        return safe.substring(0,Math.min(safe.length(),8192));
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("invalid lifecycle event"); }
}
