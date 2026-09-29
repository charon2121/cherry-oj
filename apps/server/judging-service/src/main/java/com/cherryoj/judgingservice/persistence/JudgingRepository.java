package com.cherryoj.judgingservice.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JudgingRepository {
    private final JdbcTemplate jdbc;

    public JudgingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public EnvironmentRow findActive(boolean lock) {
        List<EnvironmentRow> rows = jdbc.query("""
                SELECT BIN_TO_UUID(id) id, name, fingerprint, endpoint_ref
                FROM judge_environment WHERE status = 'ACTIVE' ORDER BY id
                """ + (lock ? " FOR UPDATE" : ""), JudgingRepository::environment);
        if (rows.size() > 1) throw new IllegalStateException("multiple ACTIVE judge environments");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean languageEnabled(String environmentId, String languageId) {
        return !jdbc.query("""
                SELECT 1 FROM judge_environment_language
                WHERE judge_environment_id = UUID_TO_BIN(?) AND language_id = ? AND enabled = 1
                """, (rs, row) -> 1, environmentId, languageId).isEmpty();
    }

    public void insertCalibration(String id, String problemVersionId, String languageId,
                                  String environmentId, long cpuNs, long memoryBytes, Long clockNs,
                                  LocalDateTime now) {
        jdbc.update("""
                INSERT INTO language_calibration
                  (id, problem_version_id, language_id, judge_environment_id, status, source_type,
                   cpu_ns, memory_bytes, clock_ns, benchmark_summary_json, approved_by, approved_at,
                   supersedes_id, error_message, created_at, updated_at, row_version)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?), 'RUNNING', 'BENCHMARK',
                        ?, ?, ?, NULL, NULL, NULL, NULL, NULL, ?, ?, 0)
                """, id, problemVersionId, languageId, environmentId, cpuNs, memoryBytes, clockNs, now, now);
    }

    public CalibrationRow findCalibration(String id) {
        List<CalibrationRow> rows = jdbc.query(calibrationSelect() + " WHERE id = UUID_TO_BIN(?)",
                JudgingRepository::calibration, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public CalibrationRow findValid(String problemVersionId, String languageId,
                                    String environmentId, boolean lock) {
        List<CalibrationRow> rows = jdbc.query(calibrationSelect() + """
                 WHERE problem_version_id = UUID_TO_BIN(?) AND language_id = ?
                   AND judge_environment_id = UUID_TO_BIN(?) AND status = 'VALID'
                """ + (lock ? " FOR UPDATE" : ""), JudgingRepository::calibration,
                problemVersionId, languageId, environmentId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public int supersede(String id, LocalDateTime now, long rowVersion) {
        return jdbc.update("""
                UPDATE language_calibration SET status = 'SUPERSEDED', updated_at = ?, row_version = row_version + 1
                WHERE id = UUID_TO_BIN(?) AND status = 'VALID' AND row_version = ?
                """, now, id, rowVersion);
    }

    public int markCalibrationValid(String id, String actorId, String supersedesId,
                                    String summaryJson, LocalDateTime now) {
        return jdbc.update("""
                UPDATE language_calibration SET status = 'VALID', benchmark_summary_json = CAST(? AS JSON),
                    approved_by = UUID_TO_BIN(?), approved_at = ?, supersedes_id = UUID_TO_BIN(?),
                    error_message = NULL, updated_at = ?, row_version = row_version + 1
                WHERE id = UUID_TO_BIN(?) AND status = 'RUNNING'
                """, summaryJson, actorId, now, supersedesId, now, id);
    }

    public int markCalibrationFailed(String id, String summaryJson, String error, LocalDateTime now) {
        return jdbc.update("""
                UPDATE language_calibration SET status = 'FAILED', benchmark_summary_json = CAST(? AS JSON),
                    error_message = ?, updated_at = ?, row_version = row_version + 1
                WHERE id = UUID_TO_BIN(?) AND status = 'RUNNING'
                """, summaryJson, error, now, id);
    }

    public void insertAudit(String id, String aggregateType, String aggregateId, String actorId,
                            String action, String traceId, String detailJson, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO judging_audit_event
                  (id, aggregate_type, aggregate_id, actor_user_id, action, trace_id, detail_json, created_at)
                VALUES (UUID_TO_BIN(?), ?, UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, CAST(? AS JSON), ?)
                """, id, aggregateType, aggregateId, actorId, action, traceId, detailJson, now);
    }

    private static String calibrationSelect() {
        return """
                SELECT BIN_TO_UUID(id) id, BIN_TO_UUID(problem_version_id) problem_version_id,
                       language_id, BIN_TO_UUID(judge_environment_id) judge_environment_id, status,
                       cpu_ns, memory_bytes, clock_ns, CAST(benchmark_summary_json AS CHAR) benchmark_summary_json,
                       error_message, created_at, updated_at, row_version
                FROM language_calibration
                """;
    }

    private static EnvironmentRow environment(ResultSet rs, int ignored) throws SQLException {
        return new EnvironmentRow(rs.getString("id"), rs.getString("name"), rs.getString("fingerprint"),
                rs.getString("endpoint_ref"));
    }

    private static CalibrationRow calibration(ResultSet rs, int ignored) throws SQLException {
        Long clock = rs.getObject("clock_ns", Long.class);
        return new CalibrationRow(rs.getString("id"), rs.getString("problem_version_id"),
                rs.getString("language_id"), rs.getString("judge_environment_id"), rs.getString("status"),
                rs.getObject("cpu_ns", Long.class), rs.getObject("memory_bytes", Long.class), clock,
                rs.getString("benchmark_summary_json"), rs.getString("error_message"),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class),
                rs.getLong("row_version"));
    }

    public record EnvironmentRow(String id, String name, String fingerprint, String endpointRef) {}
    public record CalibrationRow(String id, String problemVersionId, String languageId, String environmentId,
                                 String status, Long cpuNs, Long memoryBytes, Long clockNs,
                                 String benchmarkSummaryJson, String errorMessage,
                                 LocalDateTime createdAt, LocalDateTime updatedAt, long rowVersion) {}
}
