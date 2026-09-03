package com.example.matching.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OptionalExternalStoreConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MilvusConfig.class, ResilientMilvusClient.class,
                    Neo4jGraphConfig.class, Neo4jGraphProperties.class)
            .withPropertyValues(
                    "milvus.enabled=true",
                    "neo4j.graph.enabled=true");

    @Test
    void missingExternalEndpointsLeavesMysqlFallbackAvailable() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MilvusConfig.class);
            assertThat(context).doesNotHaveBean(ResilientMilvusClient.class);
            assertThat(context).doesNotHaveBean(org.neo4j.driver.Driver.class);
        });
    }
}
