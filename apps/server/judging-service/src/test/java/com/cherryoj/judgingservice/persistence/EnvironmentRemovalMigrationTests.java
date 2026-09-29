package com.cherryoj.judgingservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V5 在已有数据上取消判题环境：节点保留语言与回执，同一题目版本 × 语言只留最近批准的 VALID 标定。 */
@Testcontainers(disabledWithoutDocker = true)
class EnvironmentRemovalMigrationTests {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void nodesKeepLanguagesAndReceiptsAndOnlyTheLatestValidCalibrationSurvives() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).target("4").load().migrate();
        var jdbc = new JdbcTemplate(source);
        var now = LocalDateTime.of(2026, 9, 6, 0, 0);
        String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
        for (String environment : new String[] {first, second}) {
            jdbc.update("""
                    INSERT INTO judge_environment(id,name,fingerprint,status,architecture,cpu_model,os_version,kernel_version,
                      judge_version,sandbox_version,config_digest,endpoint_ref,created_at,activated_at,row_version)
                    VALUES(UUID_TO_BIN(?),'env',?,?,'amd64','cpu','linux','kernel','v1','v1','digest','http://127.0.0.1:5051',?,?,0)
                    """, environment, environment, environment.equals(first) ? "ACTIVE" : "REGISTERED", now,
                    environment.equals(first) ? now : null);
        }
        jdbc.update("""
                INSERT INTO judge_node(node_id,judge_environment_id,session_id,endpoint,metadata_json,lease_expires_at,created_at,updated_at)
                VALUES('node-1',UUID_TO_BIN(?),UUID_TO_BIN(UUID()),'http://127.0.0.1:5051',
                  JSON_OBJECT('languages',JSON_ARRAY(JSON_OBJECT('languageId','cpp'))),?,?,?)
                """, first, now.plusSeconds(35), now, now);
        jdbc.update("""
                INSERT INTO test_data_node_deployment(test_data_version_id,node_id,expected_sha256,session_id,file_count,available,deployed_at)
                SELECT UUID_TO_BIN(UUID()),node_id,UNHEX(REPEAT('a',64)),session_id,2,1,? FROM judge_node
                """, now);
        String problemVersion = UUID.randomUUID().toString(), older = UUID.randomUUID().toString(), newer = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO judging_audit_event(id,aggregate_type,aggregate_id,action,created_at) VALUES(UUID_TO_BIN(UUID()),'ENVIRONMENT',UUID_TO_BIN(?),'fixture',?)", first, now);
        for (String[] row : new String[][] {{older, first, "0"}, {newer, second, "1"}}) {
            jdbc.update("""
                    INSERT INTO language_calibration(id,problem_version_id,language_id,judge_environment_id,status,source_type,
                      cpu_ns,memory_bytes,approved_by,approved_at,created_at,updated_at)
                    VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'cpp',UUID_TO_BIN(?),'VALID','MANUAL',1000,1000,UUID_TO_BIN(UUID()),?,?,?)
                    """, row[0], problemVersion, row[1], now.plusHours(Long.parseLong(row[2])), now, now.plusHours(1));
        }

        Flyway.configure().dataSource(source).load().migrate();

        assertThat(jdbc.queryForObject("SELECT CAST(languages_json AS CHAR) FROM judge_node", String.class)).isEqualTo("[\"cpp\"]");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_data_node_deployment WHERE available=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM language_calibration WHERE id=UUID_TO_BIN(?)", String.class, older)).isEqualTo("SUPERSEDED");
        assertThat(jdbc.queryForObject("SELECT status FROM language_calibration WHERE id=UUID_TO_BIN(?)", String.class, newer)).isEqualTo("VALID");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM judging_audit_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name LIKE 'judge_environment%'
                """, Integer.class)).isZero();
    }
}
