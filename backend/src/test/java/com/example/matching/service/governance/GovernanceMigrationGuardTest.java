package com.example.matching.service.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Governance DB Write Guards")
class GovernanceMigrationGuardTest {

    private String v74() {
        try {
            Path path = Paths.get("src", "main", "resources", "db", "migration",
                    "V74__add_governance_admission_table_and_write_guards.sql");
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read V74 migration", e);
        }
    }

    @Test
    @DisplayName("V74 creates governance_admission table with PASS decision")
    void migrationCreatesGovernanceAdmissionTable() {
        String sql = v74();
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS governance_admission");
        assertThat(sql).contains("final_decision VARCHAR(16)");
        assertThat(sql).contains("admission_code VARCHAR(32)");
    }

    @Test
    @DisplayName("V74 adds foreign keys on emp_ability and post_ability_model")
    void migrationAddsForeignKeys() {
        String sql = v74();
        assertThat(sql).contains("fk_emp_ability_gov_admission");
        assertThat(sql).contains("fk_post_ability_model_gov_admission");
        assertThat(sql).contains("REFERENCES governance_admission(id)");
    }

    @Test
    @DisplayName("V74 adds trigger guards requiring PASS admission for AI-sourced writes")
    void migrationAddsTriggerGuards() {
        String sql = v74();
        assertThat(sql).contains("CREATE TRIGGER trg_emp_ability_ai_guard_insert");
        assertThat(sql).contains("CREATE TRIGGER trg_emp_ability_ai_guard_update");
        assertThat(sql).contains("CREATE TRIGGER trg_post_ability_model_ai_guard_insert");
        assertThat(sql).contains("CREATE TRIGGER trg_post_ability_model_ai_guard_update");
        assertThat(sql).contains("final_decision = 'PASS'");
        assertThat(sql).contains("SIGNAL SQLSTATE '45000'");
    }

    @Test
    @DisplayName("V74 guards AI pipeline sources on emp_ability including AI_PROJECT")
    void migrationGuardsAiSources() {
        String sql = v74();
        assertThat(sql).contains("'RESUME_PARSE'");
        assertThat(sql).contains("'JD_IMPORT'");
        assertThat(sql).contains("'AI_INTERVIEW'");
        assertThat(sql).contains("'AI_PROJECT'");
        assertThat(sql).contains("'PMS_IMPORT'");
    }

    @Test
    @DisplayName("V74 post_ability_model guard uses explicit source_type marker, not remark content")
    void postAbilityModelGuardUsesSourceTypeMarker() {
        String sql = v74();
        assertThat(sql).contains("ALTER TABLE post_ability_model");
        assertThat(sql).contains("ADD COLUMN source_type VARCHAR(64)");
        assertThat(sql).contains("NEW.source_type IN ('JD_IMPORT','POST_EVOLUTION'");
        assertThat(sql).doesNotContain("NEW.remark LIKE 'source:%'");
    }

    @Test
    @DisplayName("V74 SQL statements are syntactically well-formed for Flyway")
    void migrationHasNoCommentGluedStatements() {
        String sql = v74();
        // 不允许同一行内出现 "注释...SQL" 粘连（Flyway 会把整行当注释吞掉）
        String[] lines = sql.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int commentIdx = line.indexOf("--");
            if (commentIdx >= 0) {
                String afterComment = line.substring(commentIdx + 2);
                String trimmed = afterComment.trim();
                assertThat(trimmed)
                        .as("line " + (i + 1) + " has SQL glued after inline comment")
                        .doesNotStartWith("ALTER").doesNotStartWith("CREATE").doesNotStartWith("INSERT");
            }
        }
    }
}
