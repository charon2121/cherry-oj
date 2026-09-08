package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.domain.UuidV7;
import com.cherryoj.judgingservice.judge.JudgeGateway;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import com.cherryoj.judgingservice.persistence.JudgingRepository;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties={"cherry.formal.enabled=false","cherry.judging.recovery-enabled=false"})
@Testcontainers
class FormalTaskStoreTests {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("judging");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl); registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper json;
    MutableClock clock;
    FormalTaskStore store;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM outbox_event"); jdbc.update("DELETE FROM judge_attempt"); jdbc.update("DELETE FROM judge_task"); jdbc.update("DELETE FROM inbox_event");
        clock=new MutableClock(); store=new FormalTaskStore(jdbc,manager,new UuidV7(clock,new SecureRandom()),clock,json);
    }
    @Test void duplicateRequestsAndExpiredLeaseCannotProduceTwoTerminalResults() {
        String id=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
        store.receive(event,id,clock.instant(),"a".repeat(32),null);
        store.receive(event,id,clock.instant(),"a".repeat(32),null);
        store.receive(UUID.randomUUID().toString(),id,clock.instant(),"a".repeat(32),null);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM judge_task",Integer.class));
        var old=store.claim(Duration.ofSeconds(10)); assertNull(store.claim(Duration.ofSeconds(10)));
        clock.advance(11); var current=store.claim(Duration.ofSeconds(10));
        assertEquals(2,current.attemptNo()); assertFalse(store.renew(old,Duration.ofSeconds(10)));
        assertFalse(store.finish(old,Map.of("verdict","AC","environmentFingerprint","f"),null,null));
        assertTrue(store.finish(current,Map.of("verdict","WA","environmentFingerprint","f"),null,null));
        assertFalse(store.finish(current,Map.of("verdict","AC","environmentFingerprint","f"),null,null));
        assertEquals(1,store.pending().stream().filter(e -> e.payload().contains("JudgeCompleted")).count());
        assertEquals("ABANDONED",jdbc.queryForObject("SELECT status FROM judge_attempt WHERE attempt_no=1",String.class));
        assertNull(store.claim(Duration.ofSeconds(10)));
    }
    @Test void nodeFailureRetriesBoundedlyThenPublishesSeWithoutChangingFrozenEnvironment() {
        String id=UUID.randomUUID().toString(),environment=UUID.randomUUID().toString(),data=UUID.randomUUID().toString();
        store.receive(UUID.randomUUID().toString(),id,clock.instant(),"a".repeat(32),null);
        var inputs=mock(FormalInputClient.class); var nodes=mock(JudgeNodeRepository.class); var environments=mock(JudgingRepository.class); var judge=mock(JudgeGateway.class);
        var properties=new FormalProperties(false,"requests","lifecycle","http://localhost","",1,3,Duration.ofSeconds(30),Duration.ofMinutes(10),Duration.ofSeconds(30),10,Duration.ofSeconds(1));
        var input=new FormalInput(id,"2",UUID.randomUUID().toString(),UUID.randomUUID().toString(),data,"cpp","source",FormalWorker.hash("source"),environment,"f",UUID.randomUUID().toString(),new JudgeGateway.Limits(1000,2000,3000L),clock.instant(),"a".repeat(64),1,40000000000L);
        when(inputs.get(id,null)).thenReturn(input);
        try {
            var worker=new FormalWorker(store,inputs,nodes,environments,judge,properties,clock);
            for(int attempt=1;attempt<=3;attempt++) { worker.execute(store.claim(Duration.ofSeconds(30))); clock.advance(6); }
            assertNull(store.claim(Duration.ofSeconds(30)));
            assertEquals(1,store.pending().stream().filter(e -> e.payload().contains("JudgeFailed")).count());
            verify(nodes,times(3)).ready(eq(environment),eq(data),eq("a".repeat(64)),any());
            verifyNoInteractions(judge); worker.stop();
        } finally { }
    }
    static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-09-07T00:00:00Z");
        void advance(long seconds) { now=now.plusSeconds(seconds); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
