package com.example.matching.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DevelopmentRuntimeConfigurationTest {

    private static final Path DEVELOPMENT_CONFIG = Path.of("src", "main", "resources", "application-dev.yml");

    @Test
    void developmentProfileSkipsMigrationsAndStartsRabbitListeners() throws IOException {
        String config = Files.readString(DEVELOPMENT_CONFIG);

        assertThat(config).containsPattern("flyway:\\R\\s+enabled: false");
        assertThat(config).doesNotContain("auto-startup: false");
    }

    @Test
    void developmentProfileDoesNotRequireNeo4jConfiguration() throws IOException {
        String config = Files.readString(DEVELOPMENT_CONFIG);

        assertThat(config).doesNotContain("neo4j:");
    }
}
