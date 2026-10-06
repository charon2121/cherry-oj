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

    public void insertCalibration(String id, String problemId, String languageId, String testDataDigest,
                                  long cpuNs, long memoryBytes, Long clockNs, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO language_calibration
                  (id, problem_id, language_id, test_data_digest, status, source_type,
                   cpu_ns, memory_bytes, clock_ns, benchmark_summary_json, approved_by, approved_at,
                   supersedes_id, error_message, created_at, updated_at, row_version)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, 'RUNNING', 'BENCHMARK',
                        ?, ?, ?, NULL, NULL, NULL, NULL, NULL, ?, ?, 0)
                """, id, problemId, languageId, testDataDigest, cpuNs, memoryBytes, clockNs, now, now);
    }

    public CalibrationRow findCalibration(String id) {
        List<CalibrationRow> rows = jdbc.query(calibrationSelect() + " WHERE id = UUID_TO_BIN(?)",
                JudgingRepository::calibration, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public CalibrationRow findValid(String problemId, String languageId, boolean lock) {
        List<CalibrationRow> rows = jdbc.query(calibrationSelect() + """
                 WHERE problem_id = UUID_TO_BIN(?) AND language_id = ? AND status = 'VALID'
                """ + (lock ? " FOR UPDATE" : ""), JudgingRepository::calibration,
                problemId, languageId);
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
                SELECT BIN_TO_UUID(id) id, BIN_TO_UUID(problem_id) problem_id,
                       language_id, test_data_digest, status,
                       cpu_ns, memory_bytes, clock_ns, CAST(benchmark_summary_json AS CHAR) benchmark_summary_json,
                       error_message, created_at, updated_at, row_version
                FROM language_calibration
                """;
    }

    private static CalibrationRow calibration(ResultSet rs, int ignored) throws SQLException {
        Long clock = rs.getObject("clock_ns", Long.class);
        return new CalibrationRow(rs.getString("id"), rs.getString("problem_id"),
                rs.getString("language_id"), rs.getString("test_data_digest"), rs.getString("status"),
                rs.getObject("cpu_ns", Long.class), rs.getObject("memory_bytes", Long.class), clock,
                rs.getString("benchmark_summary_json"), rs.getString("error_message"),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class),
                rs.getLong("row_version"));
    }

    public record CalibrationRow(String id, String problemId, String languageId, String testDataDigest,
                                 String status, Long cpuNs, Long memoryBytes, Long clockNs,
                                 String benchmarkSummaryJson, String errorMessage,
                                 LocalDateTime createdAt, LocalDateTime updatedAt, long rowVersion) {}
}
