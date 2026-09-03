package com.example.matching.integration.db;

import com.example.matching.infra.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for V69 — the emp_ability evaluation_source data migration.
 * <p>
 * V69 performs two operations:
 * <ol>
 *   <li>Converts NULL / empty / whitespace-only {@code evaluation_source} values
 *       to {@code 'LEGACY_'} + the row's primary key.</li>
 *   <li>Makes the column {@code NOT NULL DEFAULT 'UNKNOWN'}.</li>
 * </ol>
 * <p>
 * This test runs on a real MySQL 8 instance via Testcontainers. Spring's automatic
 * Flyway execution is disabled so we can control migration boundaries precisely:
 * we run up to V68, insert pre-migration data, then apply V69 and verify.
 * <p>
 * <b>V28 ordering workaround:</b> V28 ALTERs tables that are created in V30,
 * but Flyway applies V28 before V30 (by version number). We work around this
 * by pre-creating V30's tables via raw SQL before invoking Flyway. When Flyway
 * later reaches V30 its {@code CREATE TABLE IF NOT EXISTS} statements are no-ops.
 */
@TestPropertySource(properties = "spring.flyway.enabled=false")
class V69EmpAbilityMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------ //
    //  V69 data-migration test                                            //
    // ------------------------------------------------------------------ //

    @Test
    void v69DataMigrationConvertsNullAndEmptySources() throws Exception {
        // ── Step 1: work around the V28-before-V30 ordering issue ────────
        preCreateV30BaseTables();

        // ── Step 2: run Flyway V27 → V68 ────────────────────────────────
        Flyway flywayToV68 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("68")
                .outOfOrder(true)
                .load();
        flywayToV68.migrate();

        // ── Step 3: insert pre-migration test data ──────────────────────
        //    evaluation_source can still be NULL at this point (V68 adds the
        //    unique constraint but does NOT enforce NOT NULL).
        jdbcTemplate.update(
                "INSERT INTO emp_ability (emp_id, tag_id, evaluation_source, is_deleted, version) "
                        + "VALUES (?, ?, NULL, 0, 1)", 1L, 100L);
        long nullSourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM emp_ability WHERE emp_id = 1 AND tag_id = 100", Long.class);

        jdbcTemplate.update(
                "INSERT INTO emp_ability (emp_id, tag_id, evaluation_source, is_deleted, version) "
                        + "VALUES (?, ?, '', 0, 1)", 1L, 101L);
        long emptySourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM emp_ability WHERE emp_id = 1 AND tag_id = 101", Long.class);

        jdbcTemplate.update(
                "INSERT INTO emp_ability (emp_id, tag_id, evaluation_source, is_deleted, version) "
                        + "VALUES (?, ?, '   ', 0, 1)", 1L, 102L);
        long whitespaceSourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM emp_ability WHERE emp_id = 1 AND tag_id = 102", Long.class);

        jdbcTemplate.update(
                "INSERT INTO emp_ability (emp_id, tag_id, evaluation_source, is_deleted, version) "
                        + "VALUES (?, ?, 'MANUAL', 0, 1)", 2L, 200L);
        long manualSourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM emp_ability WHERE emp_id = 2 AND tag_id = 200", Long.class);

        jdbcTemplate.update(
                "INSERT INTO emp_ability (emp_id, tag_id, evaluation_source, is_deleted, version) "
                        + "VALUES (?, ?, NULL, 0, 1)", 2L, 201L);
        long anotherNullId = jdbcTemplate.queryForObject(
                "SELECT id FROM emp_ability WHERE emp_id = 2 AND tag_id = 201", Long.class);

        long countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM emp_ability", Long.class);
        assertThat(countBefore).isEqualTo(5L);

        // ── Step 4: apply V69 ───────────────────────────────────────────
        Flyway flywayToV69 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("69")
                .load();
        flywayToV69.migrate();

        // ── Step 5: assert NULL / empty / whitespace → LEGACY_<id> ──────
        assertThat(evaluationSourceOf(nullSourceId))
                .isEqualTo("LEGACY_" + nullSourceId);
        assertThat(evaluationSourceOf(emptySourceId))
                .isEqualTo("LEGACY_" + emptySourceId);
        assertThat(evaluationSourceOf(whitespaceSourceId))
                .isEqualTo("LEGACY_" + whitespaceSourceId);
        assertThat(evaluationSourceOf(anotherNullId))
                .isEqualTo("LEGACY_" + anotherNullId);

        // ── Step 6: assert 'MANUAL' is untouched ────────────────────────
        assertThat(evaluationSourceOf(manualSourceId)).isEqualTo("MANUAL");

        // ── Step 7: assert record count unchanged ───────────────────────
        long countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM emp_ability", Long.class);
        assertThat(countAfter).isEqualTo(countBefore);

        // ── Step 8: assert column is now NOT NULL DEFAULT 'UNKNOWN' ─────
        Map<String, Object> colDef = jdbcTemplate.queryForMap(
                "SHOW COLUMNS FROM emp_ability WHERE Field = 'evaluation_source'");
        assertThat(colDef.get("Null")).isEqualTo("NO");
        assertThat(colDef.get("Default")).isEqualTo("UNKNOWN");

        // ── Step 9: assert unique constraint exists ─────────────────────
        List<Map<String, Object>> uniqueIndex = jdbcTemplate.queryForList(
                "SHOW INDEX FROM emp_ability WHERE Key_name = 'uk_emp_tag_source'");
        assertThat(uniqueIndex)
                .as("unique constraint uk_emp_tag_source should exist")
                .isNotEmpty();

        // ── Step 10: new insert without explicit source → DEFAULT 'UNKNOWN'
        jdbcTemplate.update(
                "INSERT INTO emp_ability (emp_id, tag_id, is_deleted, version) "
                        + "VALUES (?, ?, 0, 1)", 3L, 300L);
        String defaultSource = jdbcTemplate.queryForObject(
                "SELECT evaluation_source FROM emp_ability WHERE emp_id = 3 AND tag_id = 300",
                String.class);
        assertThat(defaultSource).isEqualTo("UNKNOWN");

        // ── Step 11: duplicate (emp_id, tag_id, 'UNKNOWN') must fail ────
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO emp_ability (emp_id, tag_id, is_deleted, version) "
                                + "VALUES (?, ?, 0, 1)", 3L, 300L))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                            //
    // ------------------------------------------------------------------ //

    private String evaluationSourceOf(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT evaluation_source FROM emp_ability WHERE id = ?", String.class, id);
    }

    /**
     * Execute V30's CREATE TABLE statements directly so that V28's ALTER TABLE
     * commands succeed even though Flyway applies V28 before V30 by version number.
     * When Flyway later reaches V30, its {@code CREATE TABLE IF NOT EXISTS}
     * statements are harmless no-ops.
     */
    private void preCreateV30BaseTables() throws IOException {
        ClassPathResource resource = new ClassPathResource("db/migration/V30__Create_base_tables.sql");
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        for (String stmt : sql.split(";")) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty()) {
                try {
                    jdbcTemplate.execute(trimmed);
                } catch (Exception e) {
                    // IF NOT EXISTS makes re-execution safe; log and continue
                    // for any "already exists" type errors.
                }
            }
        }
    }
}
