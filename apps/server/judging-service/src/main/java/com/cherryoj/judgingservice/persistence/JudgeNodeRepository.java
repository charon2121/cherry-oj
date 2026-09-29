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
    private static final String SELECT = """
            SELECT n.node_id, BIN_TO_UUID(n.session_id) session_id, n.endpoint, n.lease_expires_at
            FROM judge_node n
            """;
    private static Node row(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new Node(rs.getString("node_id"), rs.getString("session_id"),
                rs.getString("endpoint"), rs.getObject("lease_expires_at", LocalDateTime.class));
    }
    public Node find(String id) {
        var rows = jdbc.query(SELECT + " WHERE n.node_id=?", JudgeNodeRepository::row, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public List<Node> online(LocalDateTime now) {
        return jdbc.query(SELECT + " WHERE n.lease_expires_at>? ORDER BY n.node_id", JudgeNodeRepository::row, now);
    }
    /** 在线且声明了该语言的节点。 */
    public List<Node> online(String languageId, LocalDateTime now) {
        return jdbc.query(SELECT + " WHERE n.lease_expires_at>? AND JSON_CONTAINS(n.languages_json, JSON_QUOTE(?)) ORDER BY n.node_id",
                JudgeNodeRepository::row, now, languageId);
    }
    /** 在线、声明了该语言、且本会话已按摘要安装这份测试数据的节点；判题只路由给它。 */
    public Node ready(String languageId, String versionId, String sha, LocalDateTime now) {
        var rows = jdbc.query(SELECT + """
                 JOIN test_data_node_deployment d ON d.node_id=n.node_id AND d.session_id=n.session_id
                 WHERE n.lease_expires_at>? AND JSON_CONTAINS(n.languages_json, JSON_QUOTE(?))
                 AND d.test_data_version_id=UUID_TO_BIN(?) AND d.expected_sha256=UNHEX(?) AND d.available=1
                 ORDER BY n.node_id LIMIT 1
                """, JudgeNodeRepository::row, now, languageId, versionId, sha);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public void register(Registration r, String languages, LocalDateTime now, LocalDateTime expiry) {
        // 保留历史安装事实；新进程必须通过幂等安装重新证明它持有的数据。
        jdbc.update("UPDATE test_data_node_deployment SET available=0 WHERE node_id=? AND session_id<>UUID_TO_BIN(?)", r.nodeId(), r.sessionId());
        jdbc.update("""
                INSERT INTO judge_node(node_id,session_id,endpoint,languages_json,lease_expires_at,created_at,updated_at)
                VALUES (?,UUID_TO_BIN(?),?,CAST(? AS JSON),?,?,?)
                ON DUPLICATE KEY UPDATE session_id=VALUES(session_id),endpoint=VALUES(endpoint),languages_json=VALUES(languages_json),
                lease_expires_at=VALUES(lease_expires_at),updated_at=VALUES(updated_at)
                """, r.nodeId(), r.sessionId(), r.endpoint(), languages, expiry, now, now);
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
    public record Node(String nodeId, String sessionId, String endpoint, LocalDateTime leaseExpiresAt) {}
}
