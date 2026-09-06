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

@SpringBootTest(properties = {"cherry.judging.recovery-enabled=false", "cherry.judging.provision.enabled=false"})
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
    @Test void registrationLeaseRestartAndConcurrentFirstEnvironmentPreserveFacts() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-06T00:00:00Z"));
        when(clock.millis()).thenReturn(Instant.parse("2026-09-06T00:00:00Z").toEpochMilli());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        Registration fixture = json.readValue(json.readTree(Files.readString(Path.of("../../../contracts/judge-node.schema.json")))
                .path("$defs").path("Registration").path("examples").get(0).toString(), Registration.class);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> registry.register(fixture));
            var b = executor.submit(() -> registry.register(copy(fixture, "second", fixture.environmentFingerprint(), fixture.sessionId())));
            assertThat(a.get().environmentId()).isEqualTo(b.get().environmentId());
        }
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        String body = json.writeValueAsString(fixture);
        var denied = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/judge-nodes/v1/register")
                .contentType("application/json").content(body)).andReturn().getResponse();
        assertThat(denied.getStatus()).isEqualTo(401);
        assertThat(json.readTree(denied.getContentAsString()).size()).isEqualTo(2);
        var valid = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/judge-nodes/v1/register")
                .header("Authorization", "Bearer local-judge-control-token").contentType("application/json").content(body)).andReturn().getResponse();
        assertThat(valid.getStatus()).isEqualTo(200);
        var invalid = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/judge-nodes/v1/register")
                .header("Authorization", "Bearer local-judge-control-token").contentType("application/json").content("{}"))
                .andReturn().getResponse();
        assertThat(invalid.getStatus()).isEqualTo(400);
        assertThat(json.readTree(invalid.getContentAsString()).size()).isEqualTo(2);
        var lease = registry.register(fixture);
        assertThat(lease.leaseDurationNs()).isEqualTo(35_000_000_000L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM judge_environment WHERE status='ACTIVE'", Integer.class)).isEqualTo(1);
        var later = registry.register(copy(fixture, "other", "other-fingerprint", fixture.sessionId()));
        assertThat(later.environmentId()).isNotEqualTo(lease.environmentId());
        assertThat(jdbc.queryForObject("SELECT status FROM judge_environment WHERE id=UUID_TO_BIN(?)", String.class, later.environmentId())).isEqualTo("REGISTERED");
        assertThatThrownBy(() -> registry.register(copy(fixture, fixture.nodeId(), "changed", fixture.sessionId()))).hasMessageContaining("conflicts");
        var beforeExpiry = LocalDateTime.of(2026, 9, 6, 0, 0, 34);
        assertThat(nodes.online(lease.environmentId(), beforeExpiry)).hasSize(2);
        var atExpiry = beforeExpiry.plusSeconds(1);
        assertThat(nodes.online(lease.environmentId(), atExpiry)).isEmpty();
        when(clock.instant()).thenReturn(atExpiry.toInstant(ZoneOffset.UTC));
        registry.heartbeat(new Heartbeat(fixture.nodeId(), fixture.environmentFingerprint(), fixture.sessionId()));
        assertThat(nodes.online(lease.environmentId(), atExpiry)).hasSize(1);
        String version = UUID.randomUUID().toString();
        nodes.recordReady(nodes.find(fixture.nodeId()), version, "a".repeat(64), 2, atExpiry);
        assertThat(nodes.ready(lease.environmentId(), version, "a".repeat(64), atExpiry)).isNotNull();
        registry.register(copy(fixture, fixture.nodeId(), fixture.environmentFingerprint(), UUID.randomUUID().toString()));
        assertThat(nodes.ready(lease.environmentId(), version, "a".repeat(64), atExpiry)).isNull();
        assertThatThrownBy(() -> registry.register(fixture)).hasMessageContaining("conflicts");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_data_node_deployment", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> registry.heartbeat(new Heartbeat(fixture.nodeId(), fixture.environmentFingerprint(), fixture.sessionId()))).hasMessageContaining("conflicts");
    }
    private Registration copy(Registration r, String id, String fingerprint, String session) {
        return new Registration(id, fingerprint, session, r.endpoint(), r.architecture(), r.cpuModel(), r.osVersion(),
                r.kernelVersion(), r.judgeVersion(), r.sandboxVersion(), r.configDigest(), r.languages());
    }
}
