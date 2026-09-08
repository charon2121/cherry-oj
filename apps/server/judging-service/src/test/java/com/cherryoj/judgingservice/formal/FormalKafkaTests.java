package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.judge.JudgeGateway;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import com.cherryoj.judgingservice.persistence.JudgingRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties={"cherry.formal.enabled=true","cherry.formal.submission-token=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "cherry.judging.recovery-enabled=false"})
@Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FormalKafkaTests {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("judging");
    @Container static final KafkaContainer KAFKA=new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl); registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword); registry.add("spring.kafka.bootstrap-servers",KAFKA::getBootstrapServers);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String,String> kafka;
    @Autowired ObjectMapper json;
    @MockitoBean FormalInputClient inputs;
    @MockitoBean JudgeNodeRepository nodes;
    @MockitoBean JudgingRepository environments;
    @MockitoBean JudgeGateway judge;
    @Test void requestedEventIsDurableAndOnlySafeCompletionCrossesKafka() throws Exception {
        String id=UUID.randomUUID().toString(),env=UUID.randomUUID().toString(),data=UUID.randomUUID().toString();
        var input=new FormalInput(id,"2",UUID.randomUUID().toString(),UUID.randomUUID().toString(),data,"cpp","source",FormalWorker.hash("source"),env,"fingerprint",UUID.randomUUID().toString(),new JudgeGateway.Limits(1000,2000,3000L),Instant.now(),"a".repeat(64),1,40000000000L);
        when(inputs.get(eq(id),nullable(String.class))).thenReturn(input);
        when(nodes.ready(eq(env),eq(data),eq("a".repeat(64)),any())).thenReturn(new JudgeNodeRepository.Node("node",env,UUID.randomUUID().toString(),"http://test-node","fingerprint",LocalDateTime.now().plusMinutes(1)));
        when(environments.languageEnabled(env,"cpp")).thenReturn(true);
        when(judge.judge(anyString(),any(),nullable(String.class),any())).thenReturn(new JudgeGateway.JudgeResult("WA","fingerprint",1L,2L,0,"must not expose runtime text",List.of(new JudgeGateway.CaseResult(1,"WA",1L,2L))));
        String event=json.writeValueAsString(Map.of("eventId",UUID.randomUUID().toString(),"eventType","JudgeRequested","eventVersion",1,
                "occurredAt",input.createdAt().toString(),"traceId","a".repeat(32),"aggregateId",id,"payload",Map.of("submissionId",id,"judgeInputContractVersion","2")));
        kafka.send("judge.requests.v1",id,event).get(15,TimeUnit.SECONDS);
        kafka.send("judge.requests.v1",id,event).get(15,TimeUnit.SECONDS);
        Properties properties=new Properties();
        properties.put("bootstrap.servers",KAFKA.getBootstrapServers()); properties.put("group.id","test-"+UUID.randomUUID());
        properties.put("auto.offset.reset","earliest"); properties.put("enable.auto.commit","false");
        properties.put("key.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        try(var consumer=new KafkaConsumer<String,String>(properties)) {
            consumer.subscribe(List.of("judge.lifecycle.v1"));
            await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
                var records=consumer.poll(Duration.ofMillis(500));
                assertTrue(java.util.stream.StreamSupport.stream(records.spliterator(),false).anyMatch(r -> {
                    assertFalse(r.value().contains("runtime text"));
                    var node=json.readTree(r.value());
                    return id.equals(r.key()) && "JudgeCompleted".equals(node.path("eventType").asString())
                            && "WA".equals(node.path("payload").path("result").path("verdict").asString());
                }));
            });
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM judge_task",Integer.class));
        assertEquals("DONE",jdbc.queryForObject("SELECT status FROM judge_task WHERE submission_id=?",String.class,id));
        verify(judge,times(1)).judge(anyString(),any(),nullable(String.class),any());
        kafka.send("judge.requests.v1","private-source-key","{\"source\":\"private-source-body\"}").get(15,TimeUnit.SECONDS);
        try(var consumer=new KafkaConsumer<String,String>(properties)) {
            consumer.subscribe(List.of("judge.requests.v1.dlt"));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var records=consumer.poll(Duration.ofMillis(500));
                assertFalse(records.isEmpty());
                for(var record:records) {
                    assertNull(record.key());
                    assertFalse(record.value().contains("private-source"));
                    var summary=json.readTree(record.value());
                    assertEquals("INVALID_JUDGE_REQUEST",summary.path("code").asString());
                    assertTrue(summary.has("partition")); assertTrue(summary.has("offset"));
                }
            });
        }
    }
}
