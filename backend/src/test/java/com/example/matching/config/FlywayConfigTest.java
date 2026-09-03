package com.example.matching.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FlywayConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FlywayConfig.class)
            .withBean(DataSource.class, () -> mock(DataSource.class));

    @Test
    void doesNotCreateFlywayBeanWhenMigrationsAreDisabled() {
        contextRunner.withPropertyValues("spring.flyway.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(Flyway.class));
    }
}
