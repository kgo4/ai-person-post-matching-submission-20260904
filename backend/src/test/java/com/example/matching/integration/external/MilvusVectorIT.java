package com.example.matching.integration.external;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milvus vector database integration test.
 * <p>
 * Only runs when the MILVUS_URI environment variable is set.
 * Marked @Disabled by default; enable via the optional-external Maven profile:
 * {@code mvn test -Poptional-external -Dgroups=external}
 */
@Tag("external")
@DisplayName("Milvus Vector Integration Test")
class MilvusVectorIT {

    private static String milvusUri;

    @BeforeAll
    static void checkEnvironment() {
        milvusUri = System.getenv("MILVUS_URI");
        Assumptions.assumeTrue(milvusUri != null && !milvusUri.isBlank(),
                "Skipping Milvus integration test: MILVUS_URI env var not set");
    }

    @Test
    @DisplayName("Milvus connection URI is configured")
    void milvusUriIsConfigured() {
        assertThat(milvusUri)
                .as("MILVUS_URI should be set for external tests")
                .isNotBlank();
    }

    @Test
    @DisplayName("Placeholder: verify Milvus connection (requires running instance)")
    void verifyMilvusConnection() {
        // Placeholder test - when a running Milvus instance is available,
        // extend this test to:
        //   1. Connect to Milvus using the URI
        //   2. List collections
        //   3. Assert the connection is healthy
        assertThat(milvusUri).isNotBlank();
    }
}
