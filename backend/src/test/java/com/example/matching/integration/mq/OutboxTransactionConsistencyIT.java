package com.example.matching.integration.mq;

import com.example.matching.infra.AbstractIntegrationTest;
import com.example.matching.service.common.EventOutboxDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the transactional consistency of the Event Outbox pattern:
 * <ul>
 *   <li>When the enclosing business transaction rolls back, NO outbox record is created.</li>
 *   <li>When the enclosing business transaction commits, the outbox record IS created with status PENDING.</li>
 * </ul>
 *
 * <p>Uses {@link TransactionTemplate} to control commit / rollback explicitly,
 * bypassing the test framework's default auto-rollback behaviour.</p>
 */
class OutboxTransactionConsistencyIT extends AbstractIntegrationTest {

    @Autowired
    private EventOutboxDispatcher outboxDispatcher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── Rollback scenario ─────────────────────────────────────────────────────

    @Test
    void outboxEvent_notCreated_whenBusinessTransactionRollsBack() {
        // Enqueue inside a transaction that we force to roll back
        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    outboxDispatcher.enqueue(
                            "TEST_ROLLBACK",
                            "matching.exchange",
                            "test.rollback",
                            "rollback-payload");
                    status.setRollbackOnly(); // force rollback
                })
        ).isInstanceOf(RuntimeException.class);

        // Verify: outbox table must be empty (the insert was rolled back)
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE event_type = ?",
                Integer.class, "TEST_ROLLBACK");
        assertThat(count).isZero();
    }

    // ── Commit scenario ───────────────────────────────────────────────────────

    @Test
    void outboxEvent_createdWithPendingStatus_whenBusinessTransactionCommits() {
        transactionTemplate.executeWithoutResult(status ->
                outboxDispatcher.enqueue(
                        "TEST_COMMIT",
                        "matching.exchange",
                        "test.commit",
                        "commit-payload")
        );

        // Verify: outbox record exists with PENDING status
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM event_outbox WHERE event_type = ?",
                String.class, "TEST_COMMIT");
        assertThat(status).isEqualTo("PENDING");

        // Verify: payload and metadata are correct
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload FROM event_outbox WHERE event_type = ?",
                String.class, "TEST_COMMIT");
        assertThat(payload).isEqualTo("\"commit-payload\"");

        Integer attemptCount = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM event_outbox WHERE event_type = ?",
                Integer.class, "TEST_COMMIT");
        assertThat(attemptCount).isZero();

        Integer maxAttempts = jdbcTemplate.queryForObject(
                "SELECT max_attempts FROM event_outbox WHERE event_type = ?",
                Integer.class, "TEST_COMMIT");
        assertThat(maxAttempts).isEqualTo(10);
    }
}
