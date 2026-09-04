package com.example.matching.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationVersionTest {

    @Test
    void fullDatabaseExportIsAvailableForDeployment() throws IOException {
        Path databaseScript = Path.of("..", "release-staging-20260904", "线上数据库导出.sql");

        assertThat(databaseScript).exists();
        assertThat(Files.size(databaseScript)).isGreaterThan(0L);
        assertThat(Files.readString(databaseScript)).contains("CREATE TABLE");
    }
}
