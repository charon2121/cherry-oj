package com.cherryoj.submissionservice.application;

import com.cherryoj.submissionservice.api.SubmissionDtos.*;
import com.cherryoj.submissionservice.api.SubmissionException;
import com.cherryoj.submissionservice.integration.SubmissionPrerequisites;
import com.cherryoj.submissionservice.messaging.SubmissionLifecycle;
import com.cherryoj.submissionservice.persistence.SubmissionMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"cherry.submission.messaging-enabled=false","cherry.submission.accepting=false"})
@Testcontainers
class SubmissionPersistenceTests {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("submissions");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl); registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired SubmissionMapper store;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired SubmissionLifecycle lifecycle;
    @MockitoBean SubmissionPrerequisites prerequisites;
    SubmissionService service;
    final String user=UUID.randomUUID().toString();
    final UUID problem=UUID.randomUUID(), version=UUID.randomUUID();
    final String data=UUID.randomUUID().toString(), environment=UUID.randomUUID().toString(), calibration=UUID.randomUUID().toString();
    @BeforeEach void setup() {
        service=new SubmissionService(store,prerequisites,json,manager,true,true);
        var snapshot=new Snapshot(problem.toString(),version.toString(),1,"A+B",data,"a".repeat(64),"cpp","ACM",3);
        when(prerequisites.snapshot(problem.toString(),"cpp")).thenReturn(snapshot);
        when(prerequisites.profile(snapshot)).thenReturn(new Profile(version.toString(),data,"cpp",environment,"fingerprint",calibration,new Limits(1000000000L,268435456L,3000000000L),40000000000L));
    }
    Create request(String source) { return new Create(problem,version,"cpp",source); }
    @Test void concurrentRetriesCreateOneImmutableInputAndOneOutbox() throws Exception {
        String key=UUID.randomUUID().toString(); var request=request("int main() {return 0;}");
        var start=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var futures=new ArrayList<Future<Created>>();
            for(int i=0;i<8;i++) futures.add(pool.submit(() -> { start.await(); return service.create(user,key,request); }));
            start.countDown(); var ids=new HashSet<String>(); int fresh=0;
            for(var future:futures) { var result=future.get(30,TimeUnit.SECONDS); ids.add(result.view().id()); if(result.fresh()) fresh++; }
            assertEquals(1,ids.size()); assertEquals(1,fresh);
            String id=ids.iterator().next();
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE message_key=?",Integer.class,id));
            assertEquals(request.source(),service.input(id).completeSource());
            assertEquals(id,service.request(user,key).id());
            assertFalse(store.pending().stream().filter(x -> x.messageKey().equals(id)).findFirst().orElseThrow().payload().contains("int main"));
            reset(prerequisites); // 题目/环境之后不可用，同键仍必须恢复已受理提交。
            assertEquals(id,service.create(user,key,request).view().id());
            verifyNoInteractions(prerequisites);
            assertThrows(SubmissionException.class,() -> service.create(user,key,request("changed")));
            assertThrows(SubmissionException.class,() -> service.get(UUID.randomUUID().toString(),id));
        }
    }
    @Test void failedOutboxInsertRollsBackInputSubmissionAndIdempotencyKey() {
        var failing=mock(SubmissionMapper.class,org.mockito.AdditionalAnswers.delegatesTo(store));
        doThrow(new IllegalStateException("injected failure"))
                .when(failing).putOutbox(anyString(),anyString(),anyString(),nullable(String.class),any());
        var broken=new SubmissionService(failing,prerequisites,json,manager,true,true);
        String key=UUID.randomUUID().toString();
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM submission",Long.class);
        long inputs=jdbc.queryForObject("SELECT COUNT(*) FROM judge_input",Long.class);
        assertThrows(IllegalStateException.class,() -> broken.create(user,key,request("source")));
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM submission",Long.class));
        assertEquals(inputs,jdbc.queryForObject("SELECT COUNT(*) FROM judge_input",Long.class));
        assertNull(store.request(user,key));
    }
    @Test void versionMismatchAndMissingProfileNeverCreateSubmission() {
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM submission",Long.class);
        assertThrows(SubmissionException.class,() -> service.create(user,UUID.randomUUID().toString(),new Create(problem,UUID.randomUUID(),"cpp","source")));
        when(prerequisites.profile(any())).thenReturn(null);
        assertThrows(SubmissionException.class,() -> service.create(user,UUID.randomUUID().toString(),request("source")));
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM submission",Long.class));
    }
    @Test void completedCanArriveBeforeStartedButHiddenFieldsAndStateRegressionCannot() {
        String id=service.create(user,UUID.randomUUID().toString(),request("source")).view().id();
        String task=UUID.randomUUID().toString();
        Map<String,Object> result=new LinkedHashMap<>(Map.of("verdict","WA","environmentFingerprint","fingerprint","passedCount",1,"executedCount",3,"totalCount",3));
        result.put("output",Map.of("text","hidden"));
        assertThrows(IllegalArgumentException.class,() -> lifecycle.apply(id,event(id,task,"JudgeCompleted",2,result)));
        assertEquals("PENDING",service.get(user,id).status());
        result.remove("output");
        String completed=event(id,task,"JudgeCompleted",2,result);
        lifecycle.apply(id,completed); lifecycle.apply(id,completed);
        lifecycle.apply(id,event(id,task,"JudgeStarted",1,null));
        var view=service.get(user,id);
        assertEquals("DONE",view.status()); assertEquals("WA",view.verdict()); assertEquals(1,view.passedCount());
        assertFalse(json.writeValueAsString(view).contains("source"));
    }
    String event(String id,String task,String type,int attempt,Map<String,Object> result) {
        var payload=new LinkedHashMap<String,Object>(Map.of("submissionId",id,"taskId",task,"attemptNo",attempt));
        payload.put(type.equals("JudgeStarted")?"startedAt":"finishedAt",Instant.now().toString());
        if(result!=null) payload.put("result",result);
        return json.writeValueAsString(Map.of("eventId",UUID.randomUUID().toString(),"eventType",type,"eventVersion",1,
                "occurredAt",Instant.now().toString(),"traceId","a".repeat(32),"aggregateId",id,"payload",payload));
    }
}
