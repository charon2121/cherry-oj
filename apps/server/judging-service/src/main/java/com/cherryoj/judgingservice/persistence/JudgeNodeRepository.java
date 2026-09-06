package com.cherryoj.judgingservice.persistence;

import com.cherryoj.judgingservice.api.JudgeNodeDtos.Registration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JudgeNodeRepository {
    private final JdbcTemplate jdbc;
    public JudgeNodeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void lockRegistry() { jdbc.queryForObject("SELECT id FROM judge_node_registry_lock WHERE id=1 FOR UPDATE", Integer.class); }
    public String environment(String fingerprint) {
        var rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM judge_environment WHERE fingerprint=?", String.class, fingerprint);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public void createEnvironment(String id, Registration r, LocalDateTime now) {
        boolean first = jdbc.queryForObject("SELECT COUNT(*) FROM judge_environment", Long.class) == 0;
        jdbc.update("""
                INSERT INTO judge_environment
                (id,name,fingerprint,status,architecture,cpu_model,os_version,kernel_version,
                 judge_version,sandbox_version,config_digest,endpoint_ref,created_at,activated_at,row_version)
                VALUES (UUID_TO_BIN(?),?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                """, id, r.nodeId(), r.environmentFingerprint(), first ? "ACTIVE" : "REGISTERED", r.architecture(),
                r.cpuModel(), r.osVersion(), r.kernelVersion(), r.judgeVersion(), r.sandboxVersion(),
                r.configDigest(), r.endpoint(), now, first ? now : null);
        for (var language : r.languages()) {
            jdbc.update("""
                    INSERT INTO judge_environment_language
                    (judge_environment_id,language_id,toolchain_version,language_config_digest,enabled,created_at,updated_at,row_version)
                    VALUES (UUID_TO_BIN(?),?,?,?,1,?,?,0)
                    """, id, language.languageId(), language.toolchainVersion(), language.languageConfigDigest(), now, now);
        }
    }
    public boolean compatible(String environmentId, Registration r) {
        int matches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM judge_environment WHERE id=UUID_TO_BIN(?) AND architecture=?
                AND cpu_model=? AND os_version=? AND kernel_version=? AND judge_version=? AND sandbox_version=? AND config_digest=?
                """, Integer.class, environmentId, r.architecture(), r.cpuModel(), r.osVersion(), r.kernelVersion(),
                r.judgeVersion(), r.sandboxVersion(), r.configDigest());
        if (matches != 1 || jdbc.queryForObject("SELECT COUNT(*) FROM judge_environment_language WHERE judge_environment_id=UUID_TO_BIN(?)",
                Integer.class, environmentId) != r.languages().size()) return false;
        return r.languages().stream().allMatch(l -> jdbc.queryForObject("""
                SELECT COUNT(*) FROM judge_environment_language WHERE judge_environment_id=UUID_TO_BIN(?)
                AND language_id=? AND toolchain_version=? AND language_config_digest=?
                """, Integer.class, environmentId, l.languageId(), l.toolchainVersion(), l.languageConfigDigest()) == 1);
    }
    private static final String SELECT = """
            SELECT n.node_id, BIN_TO_UUID(n.judge_environment_id) environment_id,
                   BIN_TO_UUID(n.session_id) session_id, n.endpoint, e.fingerprint, n.lease_expires_at
            FROM judge_node n JOIN judge_environment e ON e.id=n.judge_environment_id
            """;
    private static Node row(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new Node(rs.getString("node_id"), rs.getString("environment_id"), rs.getString("session_id"),
                rs.getString("endpoint"), rs.getString("fingerprint"), rs.getObject("lease_expires_at", LocalDateTime.class));
    }
    public Node find(String id) {
        var rows = jdbc.query(SELECT + " WHERE n.node_id=?", JudgeNodeRepository::row, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public List<Node> online(String environmentId, LocalDateTime now) {
        return jdbc.query(SELECT + " WHERE n.judge_environment_id=UUID_TO_BIN(?) AND n.lease_expires_at>? ORDER BY n.node_id",
                JudgeNodeRepository::row, environmentId, now);
    }
    public Node ready(String environmentId, String versionId, String sha, LocalDateTime now) {
        var rows = jdbc.query(SELECT + """
                 JOIN test_data_node_deployment d ON d.node_id=n.node_id AND d.session_id=n.session_id
                 WHERE n.judge_environment_id=UUID_TO_BIN(?) AND n.lease_expires_at>?
                 AND d.test_data_version_id=UUID_TO_BIN(?) AND d.expected_sha256=UNHEX(?) AND d.available=1
                 ORDER BY n.node_id LIMIT 1
                """, JudgeNodeRepository::row, environmentId, now, versionId, sha);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public void register(String environmentId, Registration r, String metadata, LocalDateTime now, LocalDateTime expiry) {
        // 保留历史安装事实；新进程必须通过幂等安装重新证明它持有的数据。
        jdbc.update("UPDATE test_data_node_deployment SET available=0 WHERE node_id=? AND session_id<>UUID_TO_BIN(?)", r.nodeId(), r.sessionId());
        jdbc.update("""
                INSERT INTO judge_node(node_id,judge_environment_id,session_id,endpoint,metadata_json,lease_expires_at,created_at,updated_at)
                VALUES (?,UUID_TO_BIN(?),UUID_TO_BIN(?),?,CAST(? AS JSON),?,?,?)
                ON DUPLICATE KEY UPDATE session_id=VALUES(session_id),endpoint=VALUES(endpoint),metadata_json=VALUES(metadata_json),
                lease_expires_at=VALUES(lease_expires_at),updated_at=VALUES(updated_at)
                """, r.nodeId(), environmentId, r.sessionId(), r.endpoint(), metadata, expiry, now, now);
        jdbc.update("INSERT IGNORE INTO judge_node_session(node_id,session_id,registered_at) VALUES(?,UUID_TO_BIN(?),?)",
                r.nodeId(), r.sessionId(), now);
    }
    public boolean knownSession(String id, String session) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM judge_node_session WHERE node_id=? AND session_id=UUID_TO_BIN(?)",
                Integer.class, id, session) != 0;
    }
    public void heartbeat(String id, LocalDateTime now, LocalDateTime expiry) {
        jdbc.update("UPDATE judge_node SET lease_expires_at=?,updated_at=? WHERE node_id=?", expiry, now, id);
    }
    public boolean hashConflict(String nodeId, String versionId, String sha) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM test_data_node_deployment
                WHERE node_id=? AND test_data_version_id=UUID_TO_BIN(?) AND expected_sha256<>UNHEX(?)
                """, Integer.class, nodeId, versionId, sha) != 0;
    }
    public Long receiptVersion(Node node, String versionId) {
        var rows = jdbc.queryForList("SELECT row_version FROM test_data_node_deployment WHERE node_id=? AND session_id=UUID_TO_BIN(?) AND test_data_version_id=UUID_TO_BIN(?)",
                Long.class, node.nodeId(), node.sessionId(), versionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public void invalidateReceipt(Node node, String versionId, Long expectedVersion) {
        if (expectedVersion == null) return;
        jdbc.update("UPDATE test_data_node_deployment SET available=0,row_version=row_version+1 WHERE node_id=? AND session_id=UUID_TO_BIN(?) AND test_data_version_id=UUID_TO_BIN(?) AND row_version=?",
                node.nodeId(), node.sessionId(), versionId, expectedVersion);
    }
    public void recordReady(Node node, String versionId, String sha, int fileCount, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO test_data_node_deployment(test_data_version_id,node_id,expected_sha256,session_id,file_count,available,deployed_at)
                VALUES(UUID_TO_BIN(?),?,UNHEX(?),UUID_TO_BIN(?),?,1,?)
                ON DUPLICATE KEY UPDATE session_id=VALUES(session_id),file_count=VALUES(file_count),available=1,deployed_at=VALUES(deployed_at),row_version=row_version+1
                """, versionId, node.nodeId(), sha, node.sessionId(), fileCount, now);
    }
    public record Node(String nodeId, String environmentId, String sessionId, String endpoint,
                       String fingerprint, LocalDateTime leaseExpiresAt) {}
}
