import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.ObjectMapper;

/** Fixed, bounded, read-only TASK-100 evidence; never starts the application. */
public final class ReadEvidence {
    private static final String ENV = "01a08a09-23b1-7ed5-bc0f-e03002e65e4a";
    private static final String VERSION = "01a08a30-ff4c-7a71-9112-9a4dde2923e1";
    private static final String DATA = "01a081c5-1a7b-7bac-8320-e35047f40be6";

    public static void main(String[] args) {
        try {
            var config = new StandardEnvironment();
            ConfigDataEnvironmentPostProcessor.applyTo(config);
            try (var connection = DriverManager.getConnection(
                    config.getRequiredProperty("spring.datasource.url"),
                    config.getRequiredProperty("spring.datasource.username"),
                    config.getRequiredProperty("spring.datasource.password"))) {
                connection.setReadOnly(true);
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(10);
                    statement.execute("START TRANSACTION READ ONLY");
                    try {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("environment", query(connection, """
                            SELECT BIN_TO_UUID(id) id,name,status,fingerprint,row_version
                            FROM judge_environment WHERE id=UUID_TO_BIN(?) LIMIT 1
                            """, ENV));
                        result.put("calibration", query(connection, """
                            SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(problem_version_id) problemVersionId,
                              BIN_TO_UUID(judge_environment_id) environmentId,status,cpu_ns cpuNs,
                              memory_bytes memoryBytes,clock_ns clockNs,
                              CAST(benchmark_summary_json AS CHAR) benchmarkSummary,created_at
                            FROM language_calibration
                            WHERE problem_version_id=UUID_TO_BIN(?) AND judge_environment_id=UUID_TO_BIN(?)
                              AND language_id='cpp' AND status='VALID' LIMIT 2
                            """, VERSION, ENV));
                        result.put("deployment", query(connection, """
                            SELECT d.node_id,LOWER(HEX(d.expected_sha256)) sha256,d.file_count,d.available,
                              d.deployed_at,(n.lease_expires_at>UTC_TIMESTAMP(6)) online
                            FROM test_data_node_deployment d JOIN judge_node n
                              ON d.node_id=n.node_id AND d.session_id=n.session_id
                            WHERE n.judge_environment_id=UUID_TO_BIN(?)
                              AND d.test_data_version_id=UUID_TO_BIN(?) LIMIT 2
                            """, ENV, DATA));
                        result.put("switchAudit", query(connection, """
                            SELECT BIN_TO_UUID(id) id,action,CAST(detail_json AS CHAR) detail,created_at
                            FROM judging_audit_event WHERE aggregate_id=UUID_TO_BIN(?)
                              AND action='ENVIRONMENT_SWITCHED' ORDER BY created_at DESC LIMIT 1
                            """, ENV));
                        System.out.println(new ObjectMapper().writeValueAsString(result));
                    } finally {
                        connection.rollback();
                    }
                }
            }
        } catch (Exception error) {
            // Driver/config messages may contain connection strings or credentials.
            System.err.println("READ_EVIDENCE_FAILED (" + error.getClass().getSimpleName() + ")");
            System.exit(1);
        }
    }

    private static List<Map<String, Object>> query(Connection connection, String sql, String... args)
            throws java.sql.SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(10);
            statement.setMaxRows(2);
            for (int i = 0; i < args.length; ++i) statement.setString(i + 1, args[i]);
            try (var rows = statement.executeQuery()) {
                var values = new ArrayList<Map<String, Object>>();
                var metadata = rows.getMetaData();
                while (rows.next()) {
                    var row = new LinkedHashMap<String, Object>();
                    for (int i = 1; i <= metadata.getColumnCount(); ++i)
                        row.put(metadata.getColumnLabel(i), rows.getString(i));
                    values.add(row);
                }
                return values;
            }
        }
    }
}
