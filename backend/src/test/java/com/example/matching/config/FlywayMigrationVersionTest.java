package com.example.matching.config;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationVersionTest {

    private static final Pattern VERSION = Pattern.compile("^V([^_]+)__.*\\.sql$");

    @Test
    void migrationVersionsAreUnique() throws IOException {
        Path migrations = Path.of("src", "main", "resources", "db", "migration");
        HashSet<String> versions = new HashSet<>();

        try (var files = Files.list(migrations)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                Matcher matcher = VERSION.matcher(file.getFileName().toString());
                if (matcher.matches()) {
                    assertThat(versions.add(matcher.group(1)))
                            .as("duplicate Flyway version in %s", file.getFileName())
                            .isTrue();
                }
            });
        }
    }

    @Test
    void migrationFilesAreProperlyNamed() throws IOException {
        Path migrations = Path.of("src", "main", "resources", "db", "migration");
        Pattern namingConvention = Pattern.compile("^V(\\d+)__(.+)\\.sql$");

        try (var files = Files.list(migrations)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String name = file.getFileName().toString();
                Matcher matcher = namingConvention.matcher(name);
                assertThat(matcher.matches())
                        .as("migration file '%s' should match V{{number}}__description.sql pattern", name)
                        .isTrue();
                if (matcher.matches()) {
                    int versionNum = Integer.parseInt(matcher.group(1));
                    assertThat(versionNum)
                            .as("version number in '%s' should be positive", name)
                            .isGreaterThan(0);
                    assertThat(matcher.group(2))
                            .as("description in '%s' should not be blank", name)
                            .isNotBlank();
                }
            });
        }
    }

    @Test
    void kgGraphBuildTaskRetryCountHasForwardMigration() throws IOException {
        Path migration = Path.of("src", "main", "resources", "db", "migration",
                "V83__add_retry_count_to_kg_graph_build_task.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration);
        assertThat(sql).contains("ALTER TABLE kg_graph_build_task");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS retry_count");
    }

    @Disabled("P0 DEFERRED: V28 assumes tables from V30 but is numbered before V30. Must not be silently deleted.")
    @Test
    void v28MigratesBeforeV30() {
        // V28 issues ALTER TABLE on matching_record, ability_tag_candidate,
        // ability_tag_relation, rag_query_log, emp_ability, ability_tag and
        // post_prototype -- all of which are created in V30.
        // Flyway applies migrations in version order, so V28 runs before V30
        // and fails on a fresh database because the target tables do not yet exist.
        //
        // This test is intentionally disabled to keep the issue visible.
        // A proper fix would either renumber V28 to run after V30, or split V28
        // into per-table patches placed after V30.
    }
}
