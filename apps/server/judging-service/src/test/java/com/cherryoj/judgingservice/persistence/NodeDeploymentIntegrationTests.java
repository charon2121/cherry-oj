package com.cherryoj.judgingservice.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.cherryoj.judgingservice.api.JudgeNodeDtos.*;
import com.cherryoj.judgingservice.api.JudgingDtos.*;
import com.cherryoj.judgingservice.application.*;
import com.cherryoj.judgingservice.judge.*;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties={"cherry.judging.node.deployment-mode=node-remote","cherry.judging.node.control-token=test-control"})
@Testcontainers(disabledWithoutDocker=true)
class NodeDeploymentIntegrationTests {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
    @Autowired JudgeNodeRegistry registry;
    @Autowired JudgeNodeRepository nodes;
    @Autowired JudgingReadinessService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JudgeNodeClient client;
    @MockitoBean JudgeGateway judge;
    @MockitoBean Clock clock;
    @Test void remoteDeploymentUsesOnlineInstalledNodeAndNeverCreatesLegacyReady() throws Exception {
        Instant time=Instant.parse("2026-09-06T01:00:00Z");when(clock.instant()).thenReturn(time);when(clock.millis()).thenReturn(time.toEpochMilli());when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        String session=UUID.randomUUID().toString(),version=UUID.randomUUID().toString(),problem=UUID.randomUUID().toString(),actor=UUID.randomUUID().toString();
        var metadata=new DeploymentMetadata(version,"a".repeat(64),new Manifest(1,2,List.of(new ManifestFile("1.in",1,"b".repeat(64)),new ManifestFile("1.out",1,"c".repeat(64)))));
        assertThatThrownBy(()->service.deploy(metadata,new ByteArrayInputStream(new byte[0]),actor,null)).hasMessageContaining("没有在线");
        var registration=new Registration("node-1","fingerprint",session,"http://127.0.0.1:15051","arm64","cpu","linux","kernel","v1","v1","config",List.of(new Language("cpp","g++","cpp")));
        var lease=registry.register(registration);
        when(client.install(any(),any(),any(),any())).thenAnswer(call->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new Receipt("node-1","fingerprint",session,version,"a".repeat(64),2);
        });
        var deployed=service.deploy(metadata,new ByteArrayInputStream(new byte[0]),actor,null);
        assertThat(deployed.status()).isEqualTo("READY");
        service.deploy(metadata,new ByteArrayInputStream(new byte[0]),actor,null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_data_node_deployment",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_data_deployment",Integer.class)).isZero();
        when(judge.judge(anyString(),any(),any())).thenReturn(new JudgeGateway.JudgeResult("AC","fingerprint",1L,1L,100));
        service.calibrate(new CalibrationRequest(problem,problem,version,"a".repeat(64),"cpp",1000000,1000000,null,"int main(){}"),actor,null);
        var ready=service.readiness(problem,version,"a".repeat(64),"cpp");assertThat(ready.ready()).isTrue();assertThat(ready.executionProfile().endpointRef()).isEqualTo(registration.endpoint());
        verify(judge).judge(eq(registration.endpoint()),any(),any());
        // A rejected repeat install means the node no longer proves possession.
        when(client.install(any(),any(),any(),any())).thenThrow(JudgeNodeClient.mismatch());
        assertThatThrownBy(()->service.deploy(metadata,new ByteArrayInputStream(new byte[0]),actor,null)).hasMessageContaining("回执");
        assertThat(service.readiness(problem,version,"a".repeat(64),"cpp").ready()).isFalse();
        var node=nodes.find("node-1");
        Long staleReceipt=nodes.receiptVersion(node,version);
        nodes.recordReady(node,version,"a".repeat(64),2,LocalDateTime.ofInstant(time,ZoneOffset.UTC));
        // A delayed failure cannot revoke a success committed after it started.
        nodes.invalidateReceipt(node,version,staleReceipt);
        assertThat(service.readiness(problem,version,"a".repeat(64),"cpp").ready()).isTrue();
        when(clock.instant()).thenReturn(time.plusSeconds(35));
        assertThat(service.readiness(problem,version,"a".repeat(64),"cpp").ready()).isFalse();
        assertThatThrownBy(()->service.deploy(metadata,new ByteArrayInputStream(new byte[0]),actor,null)).hasMessageContaining("没有在线");
        registry.heartbeat(new Heartbeat("node-1","fingerprint",session));
        assertThat(service.readiness(problem,version,"a".repeat(64),"cpp").ready()).isTrue();
        reset(client);
        when(client.install(any(),any(),any(),any())).thenAnswer(call->{when(clock.instant()).thenReturn(time.plusSeconds(71));return new Receipt("node-1","fingerprint",session,UUID.randomUUID().toString(),"a".repeat(64),2);});
        String another=UUID.randomUUID().toString();var other=new DeploymentMetadata(another,metadata.expectedSha256(),metadata.manifest());
        assertThatThrownBy(()->service.deploy(other,new ByteArrayInputStream(new byte[0]),actor,null)).hasMessageContaining("无法连接");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_data_node_deployment",Integer.class)).isEqualTo(1);
    }
}
