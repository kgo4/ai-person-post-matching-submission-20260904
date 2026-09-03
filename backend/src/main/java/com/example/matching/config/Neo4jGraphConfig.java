package com.example.matching.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "neo4j.graph.enabled", havingValue = "true")
@ConditionalOnExpression("'${neo4j.graph.uri:}'.trim().length() > 0 && '${neo4j.graph.username:}'.trim().length() > 0 && '${neo4j.graph.password:}'.trim().length() > 0")
public class Neo4jGraphConfig {

    private static final int MAX_CONNECT_ATTEMPTS = 3;
    private static final long CONNECT_TIMEOUT_SECONDS = 3;

    @Bean
    public Driver neo4jDriver(Neo4jGraphProperties properties) {
        Driver driver = GraphDatabase.driver(
                properties.getUri(),
                AuthTokens.basic(properties.getUsername(), properties.getPassword()),
                Config.builder()
                        .withConnectionTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .withConnectionAcquisitionTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .build()
        );
        for (int attempt = 1; attempt <= MAX_CONNECT_ATTEMPTS; attempt++) {
            try {
                driver.verifyConnectivity();
                log.info("Neo4j connected: database={}", properties.getDatabase());
                return driver;
            } catch (Exception exception) {
                if (attempt == MAX_CONNECT_ATTEMPTS) {
                    log.warn("Neo4j unavailable after {} attempts; graph reads and writes will use MySQL until it recovers: {}",
                            MAX_CONNECT_ATTEMPTS, exception.getMessage());
                    return driver;
                }
                log.warn("Neo4j connection attempt {}/{} failed: {}", attempt, MAX_CONNECT_ATTEMPTS,
                        exception.getMessage());
            }
        }
        return driver;
    }
}
