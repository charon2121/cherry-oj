package com.cherryoj.problemservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ProblemMigrationTests {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4").withDatabaseName("cherry_problem_migration");

    @Test
    void migratesFromTheDeployedPypyReleaseAndPreservesExistingLanguageConfiguration(@TempDir Path previousMigrations)
            throws IOException {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        // 已部署的 31e60df0 只有这两份文件。新加的低版本 migration 也不能提前进入旧库。
        for (String name : List.of("V1__create_problem_tables.sql", "V2__add_pypy_language.sql")) {
            try (var sql = getClass().getResourceAsStream("/db/migration/" + name)) {
                Files.copy(sql, previousMigrations.resolve(name));
            }
        }
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + previousMigrations)
                .load()
                .migrate();
        var jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForList("SELECT language_id FROM language", String.class))
                .contains("pypy")
                .doesNotContain("java");
        jdbc.update("UPDATE language SET display_name = ? WHERE language_id = 'pypy'", "Existing PyPy label");

        var current = Flyway.configure().dataSource(dataSource).load();
        current.migrate();
        current.validate();

        assertThat(jdbc.queryForObject("SELECT display_name FROM language WHERE language_id = 'pypy'", String.class))
                .isEqualTo("Existing PyPy label");
        assertThat(jdbc.queryForObject("SELECT display_name FROM language WHERE language_id = 'java'", String.class))
                .isEqualTo("Java 21");
        assertThat(current.migrate().migrationsExecuted).isZero();
    }
}
