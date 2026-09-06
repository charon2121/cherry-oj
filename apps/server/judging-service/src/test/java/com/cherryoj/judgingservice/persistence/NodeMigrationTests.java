package com.cherryoj.judgingservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import com.cherryoj.judgingservice.api.JudgeNodeDtos.*;
import java.time.LocalDateTime;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class NodeMigrationTests {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Test void populatedV1FactsSurviveV2AndLegacyQueriesStillWork() {
        var source=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        Flyway.configure().dataSource(source).target("1").load().migrate();
        var jdbc=new JdbcTemplate(source);
        var nodes=new JudgeNodeRepository(jdbc);
        String environment=UUID.randomUUID().toString(),version=UUID.randomUUID().toString();
        var now=LocalDateTime.of(2026,9,6,0,0);
        var registration=new Registration("legacy-compatible","old-fingerprint",UUID.randomUUID().toString(),"http://localhost:5051","arm64","cpu","linux","kernel","v1","v1","config",List.of(new Language("cpp","g++","cpp")));
        nodes.createEnvironment(environment,registration,now);
        jdbc.update("""
                INSERT INTO test_data_deployment(test_data_version_id,judge_environment_id,expected_sha256,status,deployed_sha256,deployed_at,created_at,updated_at)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UNHEX(REPEAT('a',64)),'READY',UNHEX(REPEAT('a',64)),?,?,?)
                """,version,environment,now,now,now);
        jdbc.update("""
                INSERT INTO language_calibration(id,problem_version_id,language_id,judge_environment_id,status,source_type,cpu_ns,memory_bytes,approved_by,approved_at,created_at,updated_at)
                VALUES(UUID_TO_BIN(UUID()),UUID_TO_BIN(?),'cpp',UUID_TO_BIN(?),'VALID','MANUAL',1000,1000,UUID_TO_BIN(UUID()),?,?,?)
                """,version,environment,now,now,now);
        jdbc.update("INSERT INTO judging_audit_event(id,aggregate_type,aggregate_id,action,created_at) VALUES(UUID_TO_BIN(UUID()),'ENVIRONMENT',UUID_TO_BIN(?),'legacy-fixture',?)",environment,now);
        var before=snapshot(jdbc);
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(snapshot(jdbc)).isEqualTo(before);
        assertThat(nodes.compatible(environment,registration)).isTrue();
        nodes.register(environment,registration,"{}",now,now.plusSeconds(35));
        // Legacy READY is deliberately not promoted to a node receipt.
        assertThat(nodes.ready(environment,version,"a".repeat(64),now)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM test_data_deployment",String.class)).isEqualTo("READY");
        nodes.recordReady(nodes.find(registration.nodeId()),version,"a".repeat(64),2,now);
        assertThat(nodes.ready(environment,version,"a".repeat(64),now)).isNotNull();
        assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    private List<List<String>> snapshot(JdbcTemplate jdbc) {
        var result=new ArrayList<List<String>>();
        for(String table:List.of("judge_environment","judge_environment_language","test_data_deployment","language_calibration","judging_audit_event")) {
            result.add(jdbc.query("SELECT * FROM "+table,(rs,row)->{
                var values=new ArrayList<String>();
                for(int i=1;i<=rs.getMetaData().getColumnCount();i++) {
                    Object value=rs.getObject(i);
                    values.add(value instanceof byte[] b?HexFormat.of().formatHex(b):String.valueOf(value));
                }
                return values.toString();
            }));
        }
        return result;
    }
}
