package com.example.matching.integration.external;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Neo4j graph database integration test.
 * <p>
 * Only runs when the NEO4J_URI environment variable is set.
 * Marked @Disabled by default; enable via the optional-external Maven profile:
 * {@code mvn test -Poptional-external -Dgroups=external}
 */
@Tag("external")
@DisplayName("Neo4j Graph Integration Test")
class Neo4jGraphIT {

    private static String neo4jUri;

    @BeforeAll
    static void checkEnvironment() {
        neo4jUri = System.getenv("NEO4J_URI");
        Assumptions.assumeTrue(neo4jUri != null && !neo4jUri.isBlank(),
                "Skipping Neo4j integration test: NEO4J_URI env var not set");
    }

    @Test
    @DisplayName("Neo4j connection URI is configured")
    void neo4jUriIsConfigured() {
        assertThat(neo4jUri)
                .as("NEO4J_URI should be set for external tests")
                .isNotBlank();
    }

    @Test
    @DisplayName("Placeholder: verify Neo4j connection (requires running instance)")
    void verifyNeo4jConnection() {
        // Placeholder test - when a running Neo4j instance is available,
        // extend this test to execute a simple Cypher query like:
        //   MATCH (n) RETURN count(n) AS nodeCount
        // and assert the response is valid.
        assertThat(neo4jUri).startsWith("bolt://");
    }
}
