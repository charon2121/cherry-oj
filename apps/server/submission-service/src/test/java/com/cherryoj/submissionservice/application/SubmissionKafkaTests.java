package com.cherryoj.submissionservice.application;

import com.cherryoj.submissionservice.api.SubmissionDtos.*;
import com.cherryoj.submissionservice.integration.SubmissionPrerequisites;
import com.cherryoj.submissionservice.persistence.SubmissionMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
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
@SpringBootTest(properties={"cherry.submission.messaging-enabled=true","cherry.submission.accepting=true"})
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SubmissionKafkaTests {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("submissions");
    @Container static final KafkaContainer KAFKA=new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl); registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword); registry.add("spring.kafka.bootstrap-servers",KAFKA::getBootstrapServers);
    }
    @Autowired SubmissionService service;
    @Autowired SubmissionMapper store;
    @Autowired KafkaTemplate<String,String> kafka;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean SubmissionPrerequisites prerequisites;
    @Test void durableRequestCrossesKafkaAndDuplicateCompletionOnlyAppliesOnce() throws Exception {
        UUID problem=UUID.randomUUID(),version=UUID.randomUUID();
        String data=UUID.randomUUID().toString(),env=UUID.randomUUID().toString(),user=UUID.randomUUID().toString();
        var snapshot=new Snapshot(problem.toString(),version.toString(),1,"A+B",data,"a".repeat(64),"cpp","ACM",1);
        when(prerequisites.snapshot(problem.toString(),"cpp")).thenReturn(snapshot);
        when(prerequisites.profile(snapshot)).thenReturn(new Profile(version.toString(),data,"cpp",env,"fingerprint",UUID.randomUUID().toString(),new Limits(1000,2000,3000L),40000000000L));
        var created=service.create(user,UUID.randomUUID().toString(),new Create(problem,version,"cpp","private source marker"));
        String id=created.view().id();
        Properties properties=new Properties();
        properties.put("bootstrap.servers",KAFKA.getBootstrapServers()); properties.put("group.id","test-"+UUID.randomUUID());
        properties.put("auto.offset.reset","earliest"); properties.put("enable.auto.commit","false");
        properties.put("key.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        try(var consumer=new KafkaConsumer<String,String>(properties)) {
            consumer.subscribe(List.of("judge.requests.v1"));
            await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
                var records=consumer.poll(Duration.ofMillis(500));
                assertTrue(java.util.stream.StreamSupport.stream(records.spliterator(),false).anyMatch(r -> {
                    assertFalse(r.value().contains("private source marker")); return id.equals(r.key());
                }));
            });
        }
        String event=json.writeValueAsString(Map.of("eventId",UUID.randomUUID().toString(),"eventType","JudgeCompleted","eventVersion",1,
                "occurredAt",Instant.now().toString(),"traceId","a".repeat(32),"aggregateId",id,
                "payload",Map.of("submissionId",id,"taskId",UUID.randomUUID().toString(),"attemptNo",1,"finishedAt",Instant.now().toString(),
                        "result",Map.of("verdict","AC","environmentFingerprint","fingerprint","passedCount",1,"executedCount",1,"totalCount",1))));
        kafka.send("judge.lifecycle.v1",id,event).get(15,TimeUnit.SECONDS);
        kafka.send("judge.lifecycle.v1",id,event).get(15,TimeUnit.SECONDS);
        String lateStarted=json.writeValueAsString(Map.of("eventId",UUID.randomUUID().toString(),"eventType","JudgeStarted","eventVersion",1,
                "occurredAt",Instant.now().toString(),"traceId","a".repeat(32),"aggregateId",id,
                "payload",Map.of("submissionId",id,"taskId",json.readTree(event).path("payload").path("taskId").asString(),
                        "attemptNo",1,"startedAt",Instant.now().toString())));
        kafka.send("judge.lifecycle.v1",id,lateStarted).get(15,TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals("AC",service.get(user,id).verdict()); assertEquals(1,store.get(id).rowVersion());
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_event",Integer.class));
        });
    }
}
