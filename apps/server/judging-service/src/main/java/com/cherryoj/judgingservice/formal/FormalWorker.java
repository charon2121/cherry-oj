package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.judge.JudgeGateway;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import com.cherryoj.judgingservice.persistence.JudgingRepository;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="cherry.formal.enabled",havingValue="true")
public class FormalWorker {
    private final FormalTaskStore store;
    private final FormalInputClient inputs;
    private final JudgeNodeRepository nodes;
    private final JudgingRepository environments;
    private final JudgeGateway judge;
    private final FormalProperties properties;
    private final Clock clock;
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String,FormalTaskStore.Task> active=new ConcurrentHashMap<>();
    private final Semaphore slots;
    public FormalWorker(FormalTaskStore store,FormalInputClient inputs,JudgeNodeRepository nodes,JudgingRepository environments,
                        JudgeGateway judge,FormalProperties properties,Clock clock) {
        this.store=store; this.inputs=inputs; this.nodes=nodes; this.environments=environments; this.judge=judge;
        this.properties=properties; this.clock=clock; slots=new Semaphore(properties.parallelism());
    }
    @Scheduled(fixedDelay=500,scheduler="formalTaskScheduler")
    public void poll() {
        for(var task:active.values()) store.renew(task,properties.leaseDuration());
        while(slots.tryAcquire()) {
            FormalTaskStore.Task task;
            try { task=store.claim(properties.leaseDuration()); }
            catch(RuntimeException error) { slots.release(); throw error; }
            if(task==null) { slots.release(); return; }
            active.put(task.id(),task);
            try { executor.submit(() -> {
                try { execute(task); }
                finally { active.remove(task.id(),task); slots.release(); }
            }); } catch(RejectedExecutionException stopping) { active.remove(task.id(),task); slots.release(); return; }
        }
    }
    public void execute(FormalTaskStore.Task task) {
        Instant deadline=task.createdAt().toInstant(ZoneOffset.UTC).plus(properties.deadline());
        try {
            if(!clock.instant().isBefore(deadline)) throw new FormalFailure("JUDGE_DEADLINE_EXCEEDED");
            if(task.attemptNo()>properties.maxAttempts()) throw new FormalFailure("JUDGE_ATTEMPTS_EXHAUSTED");
            var input=inputs.get(task.submissionId(),task.traceParent());
            validate(input,task.submissionId());
            Duration budget=Duration.ofNanos(input.executionBudgetNs());
            if(budget.isNegative() || budget.isZero() || clock.instant().plus(budget).isAfter(deadline)) throw new FormalFailure("JUDGE_BUDGET_UNAVAILABLE");
            var node=nodes.ready(input.judgeEnvironmentId(),input.testDataVersionId(),input.testDataContentSha256(),LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
            if(node==null || !node.fingerprint().equals(input.environmentFingerprint())
                    || !environments.languageEnabled(input.judgeEnvironmentId(),input.languageId())) throw new FormalFailure("NO_MATCHING_JUDGE_NODE");
            var result=judge.judge(node.endpoint(),new JudgeGateway.JudgeRequest(input.submissionId(),input.problemId(),input.problemVersionId(),
                    input.testDataVersionId(),input.languageId(),input.completeSource(),input.effectiveLimits(),"submit"),task.traceParent(),budget);
            if(!input.environmentFingerprint().equals(result.environmentFingerprint())) throw new FormalFailure("JUDGE_FINGERPRINT_MISMATCH");
            if("SE".equals(result.verdict())) throw new FormalFailure("JUDGE_SYSTEM_ERROR");
            store.finish(task,safe(result,input.totalCount()),null,null);
        } catch(Exception error) {
            String code=error instanceof FormalFailure ? error.getMessage() : "JUDGE_EXECUTION_FAILED";
            boolean retry=task.attemptNo()<properties.maxAttempts() && clock.instant().isBefore(deadline)
                    && !Set.of("INVALID_JUDGE_INPUT","JUDGE_BUDGET_UNAVAILABLE").contains(code);
            store.finish(task,null,code,retry?Duration.ofSeconds(task.attemptNo()==1?1:5):null);
        }
    }
    private static void validate(FormalInput input,String id) {
        if(input==null || !id.equals(input.submissionId()) || !"2".equals(input.contractVersion()) || !"cpp".equals(input.languageId())
                || input.completeSource()==null || input.completeSource().getBytes(StandardCharsets.UTF_8).length>262144
                || input.sourceSha256()==null || !hash(input.completeSource()).equals(input.sourceSha256())
                || input.effectiveLimits()==null || input.effectiveLimits().cpuNs()<=0 || input.effectiveLimits().memoryBytes()<=0
                || (input.effectiveLimits().clockNs()!=null && input.effectiveLimits().clockNs()<=0)
                || input.executionBudgetNs()<=0 || input.totalCount()<1 || input.totalCount()>1000 || input.createdAt()==null || input.testDataContentSha256()==null
                || !input.testDataContentSha256().matches("[a-f0-9]{64}")) throw new FormalFailure("INVALID_JUDGE_INPUT");
        try {
            for(String uuid:List.of(input.problemId(),input.problemVersionId(),input.testDataVersionId(),input.judgeEnvironmentId(),input.languageCalibrationId())) UUID.fromString(uuid);
        } catch(RuntimeException error) { throw new FormalFailure("INVALID_JUDGE_INPUT"); }
    }
    static Map<String,Object> safe(JudgeGateway.JudgeResult result,int totalCount) {
        Set<String> verdicts=Set.of("AC","WA","PE","TLE","MLE","OLE","RE","CE");
        if(result==null || result.verdict()==null || !verdicts.contains(result.verdict())) throw new FormalFailure("INVALID_JUDGE_RESULT");
        var safe=new LinkedHashMap<String,Object>();
        safe.put("verdict",result.verdict()); safe.put("environmentFingerprint",result.environmentFingerprint());
        if(result.cpuNs()!=null) { if(result.cpuNs()<0) throw new FormalFailure("INVALID_JUDGE_RESULT"); safe.put("cpuNs",result.cpuNs()); }
        if(result.memoryBytes()!=null) { if(result.memoryBytes()<0) throw new FormalFailure("INVALID_JUDGE_RESULT"); safe.put("memoryBytes",result.memoryBytes()); }
        if(result.caseResults()!=null) {
            if(result.caseResults().size()>totalCount) throw new FormalFailure("INVALID_JUDGE_RESULT");
            int passed=0,index=0;
            for(var test:result.caseResults()) {
                if(test.idx()!=++index || !verdicts.contains(test.verdict())) throw new FormalFailure("INVALID_JUDGE_RESULT");
                if("AC".equals(test.verdict())) passed++;
            }
            safe.put("passedCount",passed); safe.put("executedCount",index); safe.put("totalCount",totalCount);
        }
        if("CE".equals(result.verdict()) && result.message()!=null && !result.message().isBlank()) {
            String diagnostic=result.message().replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "")
                    .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                    .replaceAll("(?:[A-Za-z]:)?[/\\\\](?:[^\\s:]+[/\\\\])*[^\\s:]+", "<file>");
            safe.put("message",diagnostic.substring(0,Math.min(diagnostic.length(),8192)));
        }
        return safe;
    }
    static String hash(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    @PreDestroy public void stop() { executor.shutdownNow(); }
}
