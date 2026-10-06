package com.example.matching.service.kg;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GraphChangeSetSchemaMigrationTest {

    @Test
    void graphChangeSetMigrationRemovesStatusFromTheEntityUniquenessConstraint() throws Exception {
        Path migration = Path.of("src", "main", "resources", "db", "migration",
                "V84__fix_kg_graph_change_set_status_unique_index.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration);
        assertThat(sql).contains("DROP INDEX uk_kg_graph_change_set_entity");
        assertThat(sql).contains("ADD INDEX idx_kg_graph_change_set_entity_operation");
    }
}
