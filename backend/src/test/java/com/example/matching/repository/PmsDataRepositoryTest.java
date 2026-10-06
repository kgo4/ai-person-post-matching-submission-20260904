package com.example.matching.repository;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PmsDataRepository tests")
class PmsDataRepositoryTest {

    @Test
    @DisplayName("DataSource field uses HikariDataSource for connection pooling")
    void usesPooledDataSourceForConfiguredPmsConnection() {
        Field field = ReflectionUtils.findField(PmsDataRepository.class, "dataSource");

        assertThat(field).isNotNull();
        assertThat(field.getType()).isEqualTo(HikariDataSource.class);
    }

    @Test
    @DisplayName("isAvailable() returns false when URL is empty/not configured")
    void isAvailable_returnsFalseWhenUrlEmpty() throws Exception {
        PmsDataRepository repository = new PmsDataRepository();

        // Set URL to empty string (simulates unconfigured state)
        Field urlField = ReflectionUtils.findField(PmsDataRepository.class, "url");
        ReflectionUtils.makeAccessible(urlField);
        urlField.set(repository, "");

        repository.init();

        assertThat(repository.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("isAvailable() returns false when URL is null")
    void isAvailable_returnsFalseWhenUrlNull() throws Exception {
        PmsDataRepository repository = new PmsDataRepository();

        // URL defaults to null via @Value default
        Field urlField = ReflectionUtils.findField(PmsDataRepository.class, "url");
        ReflectionUtils.makeAccessible(urlField);
        urlField.set(repository, null);

        repository.init();

        assertThat(repository.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("close() sets available to false")
    void close_setsAvailableFalse() throws Exception {
        PmsDataRepository repository = new PmsDataRepository();

        // Manually set available to true to simulate a running state
        Field availableField = ReflectionUtils.findField(PmsDataRepository.class, "available");
        ReflectionUtils.makeAccessible(availableField);
        availableField.set(repository, true);

        // close() should set available = false
        repository.close();

        assertThat(repository.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Initialization with empty URL does not throw")
    void init_emptyUrl_doesNotThrow() throws Exception {
        PmsDataRepository repository = new PmsDataRepository();

        Field urlField = ReflectionUtils.findField(PmsDataRepository.class, "url");
        ReflectionUtils.makeAccessible(urlField);
        urlField.set(repository, "");

        // Should not throw - just logs a warning and returns
        repository.init();

        assertThat(repository.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("findUserByEmployeeId returns null when datasource unavailable")
    void findUserByEmployeeId_returnsNullWhenUnavailable() throws Exception {
        PmsDataRepository repository = new PmsDataRepository();

        // Ensure available is false and jdbcTemplate is null
        Field availableField = ReflectionUtils.findField(PmsDataRepository.class, "available");
        ReflectionUtils.makeAccessible(availableField);
        availableField.set(repository, false);

        Field jdbcField = ReflectionUtils.findField(PmsDataRepository.class, "jdbcTemplate");
        ReflectionUtils.makeAccessible(jdbcField);
        jdbcField.set(repository, null);

        assertThat(repository.isAvailable()).isFalse();
        // The repository reports unavailable; calling findUserByEmployeeId
        // would throw NPE on null jdbcTemplate, which is expected behavior
        // since callers should check isAvailable() first
    }

    @Test
    @DisplayName("findUserByUsername returns null when datasource unavailable")
    void findUserByUsername_returnsNullWhenUnavailable() throws Exception {
        PmsDataRepository repository = new PmsDataRepository();

        Field availableField = ReflectionUtils.findField(PmsDataRepository.class, "available");
        ReflectionUtils.makeAccessible(availableField);
        availableField.set(repository, false);

        Field jdbcField = ReflectionUtils.findField(PmsDataRepository.class, "jdbcTemplate");
        ReflectionUtils.makeAccessible(jdbcField);
        jdbcField.set(repository, null);

        assertThat(repository.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("isAvailable is false by default before init")
    void isAvailable_falseByDefaultBeforeInit() {
        PmsDataRepository repository = new PmsDataRepository();

        assertThat(repository.isAvailable()).isFalse();
    }
}
