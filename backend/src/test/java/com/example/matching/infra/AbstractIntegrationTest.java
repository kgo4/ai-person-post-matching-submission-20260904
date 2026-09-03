package com.example.matching.infra;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

/**
 * Base class for integration tests using Testcontainers.
 * <p>
 * Starts MySQL 8, Redis 7 and RabbitMQ 3 once per JVM;
 * injects dynamic connection properties so Spring Boot auto-configuration picks
 * them up without any YAML port magic.
 * <p>
 * Concrete test classes should NOT start the full application context
 * (use {@code @SpringBootTest(classes = {...})} with selected configs
 * or plain {@code @DataJpaTest}/{@code @SpringJUnitConfig}).
 */
@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    // ── Containers (static → shared across all tests in the JVM) ────────

    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("matching_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withStartupTimeout(Duration.ofMinutes(2));

    protected static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3-management")
                    .withStartupTimeout(Duration.ofMinutes(2));

    // ── Redis is started dynamically (testcontainers doesn't have a native Redis container
    //    in all versions; we use a generic container or embedded fallback) ──
    protected static final org.testcontainers.containers.GenericContainer<?> REDIS =
            new org.testcontainers.containers.GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .withStartupTimeout(Duration.ofMinutes(1));

    static {
        MYSQL.start();
        REDIS.start();
        RABBIT.start();
    }

    // ── Dynamic properties ──────────────────────────────────────────────

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // RabbitMQ
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));

        // Flyway — enabled for integration tests
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
