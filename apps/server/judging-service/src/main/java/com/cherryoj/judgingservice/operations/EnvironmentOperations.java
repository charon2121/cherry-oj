package com.cherryoj.judgingservice.operations;

import com.cherryoj.judgingservice.domain.UuidV7;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 运维显式切换环境；不复用旧环境的部署或标定事实。 */
public final class EnvironmentOperations {

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	private final UuidV7 ids = new UuidV7(Clock.systemUTC(), new SecureRandom());

	public EnvironmentOperations(DataSource source) {
		jdbc = new JdbcTemplate(source);
		jdbc.setQueryTimeout(15);
		transaction = new TransactionTemplate(new JdbcTransactionManager(source));
		transaction.setTimeout(20);
	}

	public List<Map<String, Object>> list() {
		return jdbc.queryForList("""
				SELECT BIN_TO_UUID(e.id) id,e.name,e.status,e.row_version,
				  (SELECT COUNT(*) FROM judge_node n WHERE n.judge_environment_id=e.id
				     AND n.lease_expires_at>UTC_TIMESTAMP(6)) online_nodes
				FROM judge_environment e ORDER BY e.created_at,e.id
				""");
	}

	public void switchEnvironment(String previousId, long previousVersion, String targetId, long targetVersion,
			String reason) {
		UUID.fromString(previousId);
		UUID.fromString(targetId);
		if (previousId.equalsIgnoreCase(targetId) || previousVersion < 0 || targetVersion < 0 || reason == null
				|| reason.isBlank() || reason.length() > 500 || reason.codePoints().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("INVALID_SWITCH_ARGUMENTS");
		}
		transaction.executeWithoutResult(ignored -> {
			// 与注册/心跳共享锁，在线检查与状态提交之间不会被换 session。
			jdbc.queryForObject("SELECT id FROM judge_node_registry_lock WHERE id=1 FOR UPDATE", Integer.class);
			var active = jdbc.queryForList("""
					SELECT BIN_TO_UUID(id) id,row_version FROM judge_environment WHERE status='ACTIVE' FOR UPDATE
					""");
			if (active.size() != 1 || !previousId.equalsIgnoreCase((String) active.getFirst().get("id"))
					|| ((Number) active.getFirst().get("row_version")).longValue() != previousVersion) {
				throw new IllegalStateException("ACTIVE_ENVIRONMENT_CHANGED");
			}
			var target = jdbc.queryForList("""
					SELECT status,row_version FROM judge_environment WHERE id=UUID_TO_BIN(?) FOR UPDATE
					""", targetId);
			if (target.size() != 1 || ((Number) target.getFirst().get("row_version")).longValue() != targetVersion
					|| !List.of("REGISTERED", "RETIRED").contains(target.getFirst().get("status"))) {
				throw new IllegalStateException("TARGET_ENVIRONMENT_CHANGED");
			}
			if (jdbc.queryForObject("""
					SELECT COUNT(*) FROM judge_node WHERE judge_environment_id=UUID_TO_BIN(?)
					  AND lease_expires_at>UTC_TIMESTAMP(6)
					""", Integer.class, targetId) == 0) {
				throw new IllegalStateException("TARGET_ENVIRONMENT_OFFLINE");
			}
			if (jdbc.queryForObject("""
					SELECT COUNT(*) FROM judge_environment_language
					WHERE judge_environment_id=UUID_TO_BIN(?) AND enabled=1
					""", Integer.class, targetId) == 0) {
				throw new IllegalStateException("TARGET_HAS_NO_ENABLED_LANGUAGE");
			}
			jdbc.update("""
					UPDATE judge_environment SET status='RETIRED',retired_at=UTC_TIMESTAMP(6),row_version=row_version+1
					WHERE id=UUID_TO_BIN(?)
					""", previousId);
			jdbc.update("""
					UPDATE judge_environment SET status='ACTIVE',activated_at=UTC_TIMESTAMP(6),retired_at=NULL,
					  row_version=row_version+1 WHERE id=UUID_TO_BIN(?)
					""", targetId);
			jdbc.update(
					"""
							INSERT INTO judging_audit_event
							  (id,aggregate_type,aggregate_id,action,detail_json,created_at)
							VALUES(UUID_TO_BIN(?),'ENVIRONMENT',UUID_TO_BIN(?),'ENVIRONMENT_SWITCHED',CAST(? AS JSON),UTC_TIMESTAMP(6))
							""",
					ids.next().toString(), targetId,
					new ObjectMapper().writeValueAsString(Map.of("previousEnvironmentId", previousId,
							"targetEnvironmentId", targetId, "previousRowVersion", previousVersion, "targetRowVersion",
							targetVersion, "reason", reason, "source", "operator-cli")));
		});
	}

}
