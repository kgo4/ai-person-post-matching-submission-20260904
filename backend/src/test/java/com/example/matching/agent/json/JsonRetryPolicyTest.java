package com.example.matching.agent.json;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonRetryPolicyTest {

    @Test
    void allowsAttemptsUpToMax() {
        JsonRetryPolicy policy = new JsonRetryPolicy(2, 10);
        assertTrue(policy.shouldRetry(0));
        assertTrue(policy.shouldRetry(1));
        assertFalse(policy.shouldRetry(2));
    }

    @Test
    void computesBackoffWithJitter() {
        JsonRetryPolicy policy = new JsonRetryPolicy(2, 10);
        for (int i = 0; i < 100; i++) {
            long backoff = policy.backoffMillis(0);
            assertTrue(backoff >= 0 && backoff < 10);
        }
    }
}
