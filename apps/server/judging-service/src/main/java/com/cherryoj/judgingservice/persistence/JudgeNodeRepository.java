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
    public void register(Registration r, String languages, LocalDateTime now, LocalDateTime expiry) {
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
    public record Node(String nodeId, String sessionId, String endpoint, LocalDateTime leaseExpiresAt) {}
}
