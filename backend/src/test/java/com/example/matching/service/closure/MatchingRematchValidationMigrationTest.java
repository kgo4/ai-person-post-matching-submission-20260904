package com.example.matching.service.closure;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingRematchValidationMigrationTest {

    @Test
    void createsCompositeUniqueKeyWithoutDroppingAnIndexFromANewTable() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V88__create_matching_rematch_validation.sql"));

        assertThat(sql).contains("UNIQUE KEY uk_closure_record (closure_business_key, original_matching_record_id)");
        assertThat(sql).doesNotContain("DROP INDEX closure_business_key");
    }
}
