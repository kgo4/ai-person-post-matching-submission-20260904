package com.example.matching.schedule;

import com.example.matching.service.matching.MatchingTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 匹配任务僵尸恢复：RUNNING 且长时间无状态变更（消费者进程崩溃）的任务
 * 会被 CAS 置为 FAILED，由用户重新发起。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingTaskZombieScanner {

    /** RUNNING 超过该时长无任何状态刷新即视为僵尸 */
    private static final Duration STALL_TIMEOUT = Duration.ofMinutes(30);

    private final MatchingTaskService matchingTaskService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Scheduled(fixedDelayString = "${matching.task.zombie-scan-delay-ms:60000}")
    public void scanZombieTasks() {
        runScheduled("matching_task_zombie_scan", this::scanZombieTasksInternal);
    }

    private void scanZombieTasksInternal() {
        try {
            int recovered = matchingTaskService.recoverZombieTasks(STALL_TIMEOUT);
            if (recovered > 0) {
                log.warn("匹配任务僵尸恢复完成: recovered={}", recovered);
            }
        } catch (Exception e) {
            log.error("Matching task zombie scan failed, RUNNING tasks may never be recovered", e);
        }
    }

    private void runScheduled(String taskName, Runnable task) {
        if (taskRunner != null) {
            taskRunner.run(taskName, task);
            return;
        }
        try {
            task.run();
        } catch (Exception e) {
            log.error("Matching task zombie scan failed, RUNNING tasks may never be recovered", e);
        }
    }
}
