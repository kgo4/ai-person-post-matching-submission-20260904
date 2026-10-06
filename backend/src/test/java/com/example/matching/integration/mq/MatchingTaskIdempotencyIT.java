package com.example.matching.integration.mq;

import com.example.matching.entity.matching.MatchingTask;
import com.example.matching.infra.AbstractIntegrationTest;
import com.example.matching.listener.MatchingTaskListener;
import com.example.matching.service.matching.MatchingTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies idempotency of MQ message consumption for matching tasks.
 * <p>
 * The {@link MatchingTaskListener} skips messages for tasks whose status is not 0
 * (pending). Sending the same taskId twice should result in the second invocation
 * being a no-op.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MatchingTaskIdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    private MatchingTaskListener matchingTaskListener;

    @Autowired
    private MatchingTaskService matchingTaskService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String taskId;

    @BeforeEach
    void setUp() {
        taskId = UUID.randomUUID().toString().replace("-", "");

        // Insert a matching task directly via JDBC (status=0, matchingConfig must be valid JSON)
        jdbcTemplate.update(
                "INSERT INTO matching_task (task_id, status, progress, total_count, processed_count, matching_config, created_time, updated_time) "
                        + "VALUES (?, 0, 0, 1, 0, ?, NOW(), NOW())",
                taskId,
                "{\"mode\":\"POST_EMP\",\"pairs\":[{\"postId\":1,\"empId\":100}],\"postIds\":[1],\"empIds\":[100]}");
    }

    @Test
    void duplicateMessage_secondInvocationIsSkipped() {
        // 1st invocation: task status 0 -> should be processed (claimed to status 1)
        matchingTaskListener.handleMatchingTask(taskId);

        // Wait for the claim to be visible
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    MatchingTask task = matchingTaskService.getTaskStatus(taskId);
                    assertThat(task).isNotNull();
                    assertThat(task.getStatus()).isEqualTo(1);
                });

        // 2nd invocation: task status 1 -> should be skipped (idempotent)
        matchingTaskListener.handleMatchingTask(taskId);

        // Status must still be 1, not processed again
        MatchingTask task = matchingTaskService.getTaskStatus(taskId);
        assertThat(task).isNotNull();
        assertThat(task.getStatus())
                .as("Second invocation should be skipped; status must remain 1 (executing)")
                .isEqualTo(1);
    }

    @Test
    void nonExistentTask_messageIsIgnored() {
        String ghostTaskId = UUID.randomUUID().toString().replace("-", "");

        // Should not throw, just log a warning
        matchingTaskListener.handleMatchingTask(ghostTaskId);

        // No exception = test passes; verify nothing was created
        MatchingTask task = matchingTaskService.getTaskStatus(ghostTaskId);
        assertThat(task).isNull();
    }

    @Test
    void completedTask_messageIsIgnored() {
        // Mark task as completed (status=2) before the message arrives
        matchingTaskService.completeTask(taskId, "Already done");

        // Simulate a late duplicate message
        matchingTaskListener.handleMatchingTask(taskId);

        // Status must still be 2 (completed), not re-processed
        MatchingTask task = matchingTaskService.getTaskStatus(taskId);
        assertThat(task).isNotNull();
        assertThat(task.getStatus())
                .as("Message for already-completed task should be ignored")
                .isEqualTo(2);
        assertThat(task.getResultMessage()).isEqualTo("Already done");
    }
}
