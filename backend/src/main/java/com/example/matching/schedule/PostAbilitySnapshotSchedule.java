package com.example.matching.schedule;

import com.example.matching.service.kg.KnowledgeGraphSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class PostAbilitySnapshotSchedule {

    private final KnowledgeGraphSnapshotService knowledgeGraphSnapshotService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Scheduled(cron = "${kg.snapshot.post-ability-cron:0 0 3 1 * ?}")
    public void createMonthlySnapshot() {
        runScheduled("post_ability_snapshot", this::createMonthlySnapshotInternal);
    }

    private void createMonthlySnapshotInternal() {
        knowledgeGraphSnapshotService.createPostAbilitySnapshot("MONTHLY", null);
    }

    private void runScheduled(String taskName, Runnable task) {
        if (taskRunner != null) {
            taskRunner.run(taskName, task);
            return;
        }
        try {
            task.run();
        } catch (Exception exception) {
            log.error("Scheduled post ability snapshot failed, monthly snapshot is missing", exception);
        }
    }
}
