package com.cherryoj.judgingservice.operations;

import static org.assertj.core.api.Assertions.*;

import com.cherryoj.judgingservice.api.JudgeNodeDtos.Language;
import com.cherryoj.judgingservice.api.JudgeNodeDtos.Registration;
import com.cherryoj.judgingservice.persistence.JudgeNodeRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EnvironmentOperationsTests {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

	JdbcTemplate jdbc;

	EnvironmentOperations operations;

	String previous;

	String target;

	@BeforeEach
	void setup() {
		var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
		var flyway = Flyway.configure().dataSource(source).cleanDisabled(false).load();
		flyway.clean();
		flyway.migrate();
		jdbc = new JdbcTemplate(source);
		operations = new EnvironmentOperations(source);
		previous = environment("old");
		target = environment("new");
	}

	private String environment(String name) {
		String id = UUID.randomUUID().toString();
		var r = new Registration(name, name + "-fingerprint", UUID.randomUUID().toString(), "http://127.0.0.1:5051",
				"arm64", "cpu", "linux", "kernel", "judge", "sandbox", "digest",
				List.of(new Language("cpp", "gcc", "digest")));
		var repository = new JudgeNodeRepository(jdbc);
		var now = LocalDateTime.now(ZoneOffset.UTC);
		repository.createEnvironment(id, r, now);
		repository.register(id, r, "{}", now, now.plusMinutes(5));
		return id;
	}

	@Test
	void switchesAuditsAndRollsBackWithoutChangingHistoricalReceipts() {
		String data = UUID.randomUUID().toString();
		jdbc.update(
				"""
						INSERT INTO test_data_node_deployment(test_data_version_id,node_id,expected_sha256,session_id,file_count,available,deployed_at)
						SELECT UUID_TO_BIN(?),node_id,UNHEX(?),session_id,2,1,UTC_TIMESTAMP(6) FROM judge_node WHERE node_id='old'
						""",
				data, "a".repeat(64));
		operations.switchEnvironment(previous, 0, target, 0, "Upgrade judge");
		assertThat(active()).isEqualTo(target);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM judging_audit_event WHERE action='ENVIRONMENT_SWITCHED'",
				Integer.class))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT available FROM test_data_node_deployment", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_data_node_deployment WHERE node_id='new'",
				Integer.class))
			.isZero();
		operations.switchEnvironment(target, 1, previous, 1, "Rollback");
		assertThat(active()).isEqualTo(previous);
		assertThatThrownBy(() -> operations.switchEnvironment(previous, 0, target, 0, "Stale ABA request"))
			.hasMessage("ACTIVE_ENVIRONMENT_CHANGED");
	}

	@Test
	void rejectsMissingOfflineDisabledAndStaleTargetsWithoutRetiringActive() {
		assertThatThrownBy(() -> operations.switchEnvironment(previous, 0, UUID.randomUUID().toString(), 0, "missing"))
			.hasMessage("TARGET_ENVIRONMENT_CHANGED");
		assertThatThrownBy(() -> operations.switchEnvironment(previous, 0, target, 1, "stale"))
			.hasMessage("TARGET_ENVIRONMENT_CHANGED");
		jdbc.update("UPDATE judge_node SET lease_expires_at=UTC_TIMESTAMP(6) WHERE node_id='new'");
		assertThatThrownBy(() -> operations.switchEnvironment(previous, 0, target, 0, "offline"))
			.hasMessage("TARGET_ENVIRONMENT_OFFLINE");
		jdbc.update(
				"UPDATE judge_node SET lease_expires_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 5 MINUTE) WHERE node_id='new'");
		jdbc.update("UPDATE judge_environment_language SET enabled=0 WHERE judge_environment_id=UUID_TO_BIN(?)",
				target);
		assertThatThrownBy(() -> operations.switchEnvironment(previous, 0, target, 0, "disabled"))
			.hasMessage("TARGET_HAS_NO_ENABLED_LANGUAGE");
		assertThat(active()).isEqualTo(previous);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM judging_audit_event", Integer.class)).isZero();
	}

	@Test
	void auditFailureRollsBackBothEnvironmentChanges() {
		jdbc.execute(
				"ALTER TABLE judging_audit_event ADD CONSTRAINT reject_test_audit CHECK (action <> 'ENVIRONMENT_SWITCHED')");
		assertThatThrownBy(() -> operations.switchEnvironment(previous, 0, target, 0, "audit unavailable"))
			.isInstanceOf(org.springframework.dao.DataAccessException.class);
		assertThat(active()).isEqualTo(previous);
		assertThat(jdbc.queryForObject("SELECT status FROM judge_environment WHERE id=UUID_TO_BIN(?)", String.class,
				target))
			.isEqualTo("REGISTERED");
	}

	@Test
	void concurrentSwitchOnlyAcceptsOneExpectedActiveVersion() throws Exception {
		String other = environment("other");
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(() -> attempt(target));
			var b = executor.submit(() -> attempt(other));
			assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM judge_environment WHERE status='ACTIVE'", Integer.class))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM judging_audit_event", Integer.class)).isEqualTo(1);
	}

	private boolean attempt(String id) {
		try {
			operations.switchEnvironment(previous, 0, id, 0, "concurrent upgrade");
			return true;
		}
		catch (IllegalStateException expected) {
			assertThat(expected).hasMessage("ACTIVE_ENVIRONMENT_CHANGED");
			return false;
		}
	}

	private String active() {
		return jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM judge_environment WHERE status='ACTIVE'", String.class);
	}

}
