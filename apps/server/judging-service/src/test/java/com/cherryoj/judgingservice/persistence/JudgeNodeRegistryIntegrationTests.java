package com.cherryoj.judgingservice.persistence;

import static org.assertj.core.api.Assertions.*;
import com.cherryoj.judgingservice.api.JudgeNodeDtos.*;
import com.cherryoj.judgingservice.application.JudgeNodeRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.when;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JudgeNodeRegistryIntegrationTests {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl); r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired JudgeNodeRegistry registry;
    @Autowired JudgeNodeRepository nodes;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @MockitoBean Clock clock;
    @Test void registrationLeaseRestartAndSessionReplayPreserveFacts() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-06T00:00:00Z"));
        when(clock.millis()).thenReturn(Instant.parse("2026-09-06T00:00:00Z").toEpochMilli());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        Registration fixture = json.readValue(json.readTree(Files.readString(Path.of("../../../contracts/judge-node.schema.json")))
                .path("$defs").path("Registration").path("examples").get(0).toString(), Registration.class);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> registry.register(fixture));
            var b = executor.submit(() -> registry.register(copy(fixture, "second", fixture.sessionId())));
            assertThat(a.get().nodeId()).isEqualTo(fixture.nodeId());
            assertThat(b.get().nodeId()).isEqualTo("second");
        }
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        String body = json.writeValueAsString(fixture);
        var denied = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/judge-nodes/v1/register")
                .contentType("application/json").content(body)).andReturn().getResponse();
        assertThat(denied.getStatus()).isEqualTo(401);
        assertThat(json.readTree(denied.getContentAsString()).size()).isEqualTo(2);
        var valid = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/judge-nodes/v1/register")
                .header("Authorization", "Bearer test-only-node-control-token").contentType("application/json").content(body)).andReturn().getResponse();
        assertThat(valid.getStatus()).isEqualTo(200);
        assertThat(json.readTree(valid.getContentAsString()).size()).isEqualTo(2);
        var invalid = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/judge-nodes/v1/register")
                .header("Authorization", "Bearer test-only-node-control-token").contentType("application/json").content("{}"))
                .andReturn().getResponse();
        assertThat(invalid.getStatus()).isEqualTo(400);
        assertThat(json.readTree(invalid.getContentAsString()).size()).isEqualTo(2);
        var lease = registry.register(fixture);
        assertThat(lease.leaseDurationNs()).isEqualTo(35_000_000_000L);
        var beforeExpiry = LocalDateTime.of(2026, 9, 6, 0, 0, 34);
        assertThat(nodes.online(beforeExpiry)).hasSize(2);
        assertThat(nodes.online("cpp", beforeExpiry)).hasSize(2);
        assertThat(nodes.online("python", beforeExpiry)).isEmpty();
        var atExpiry = beforeExpiry.plusSeconds(1);
        assertThat(nodes.online(atExpiry)).isEmpty();
        when(clock.instant()).thenReturn(atExpiry.toInstant(ZoneOffset.UTC));
        registry.heartbeat(new Heartbeat(fixture.nodeId(), fixture.sessionId()));
        assertThat(nodes.online(atExpiry)).hasSize(1);
        String version = UUID.randomUUID().toString();
        nodes.recordReady(nodes.find(fixture.nodeId()), version, "a".repeat(64), 2, atExpiry);
        assertThat(nodes.ready("cpp", version, "a".repeat(64), atExpiry)).isNotNull();
        assertThat(nodes.ready("python", version, "a".repeat(64), atExpiry)).isNull();
        // 同一 nodeId 的新进程接替旧进程：旧会话的回执不再可用，旧会话也不能夺回 nodeId。
        registry.register(copy(fixture, fixture.nodeId(), UUID.randomUUID().toString()));
        assertThat(nodes.ready("cpp", version, "a".repeat(64), atExpiry)).isNull();
        assertThatThrownBy(() -> registry.register(fixture)).hasMessageContaining("conflicts");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_data_node_deployment", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> registry.heartbeat(new Heartbeat(fixture.nodeId(), fixture.sessionId()))).hasMessageContaining("conflicts");
    }
    private Registration copy(Registration r, String id, String session) {
        return new Registration(id, session, r.endpoint(), r.languages());
    }
}
