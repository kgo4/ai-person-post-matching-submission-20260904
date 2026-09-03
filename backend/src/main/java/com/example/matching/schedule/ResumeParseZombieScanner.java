package com.example.matching.schedule;

import com.example.matching.service.employee.ResumeParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/**
 * 简历解析僵尸任务扫描器。
 * <p>
 * 每 2 分钟扫描一次，将处理中超过 10 分钟仍未完成的任务恢复为待处理并重新投递。
 * 适用场景：服务重启、消费者崩溃等导致任务卡在"处理中"状态。
 * <p>
 * 多实例部署时通过 ScheduledTaskRunner 分布式锁保证同一时刻仅一个实例执行扫描；
 * 数据库 CAS/状态更新仍保留，锁仅避免重复扫描，不替代幂等性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParseZombieScanner {

    private final ResumeParseService resumeParseService;
    private final SchedulerMetrics schedulerMetrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Scheduled(fixedDelay = 2 * 60 * 1000, initialDelay = 60 * 1000)
    public void scanZombieTasks() {
        if (taskRunner != null) {
            taskRunner.run("resume_parse_zombie_scan", this::scanZombieTasksInternal);
        } else {
            scanZombieTasksInternal();
        }
    }

    private void scanZombieTasksInternal() {
        try {
            int recovered = resumeParseService.recoverZombieTasks();
            if (recovered > 0) {
                log.info("简历解析僵尸任务扫描：恢复了 {} 个任务", recovered);
            }
            int reEnqueued = resumeParseService.recoverUndispatchedTasks();
            if (reEnqueued > 0) {
                log.info("简历解析未分发补偿：重新投递了 {} 个任务", reEnqueued);
            }
            // M27：等待重试（status=4）超时未投递的补投
            int retryReEnqueued = resumeParseService.recoverWaitingRetryTasks();
            if (retryReEnqueued > 0) {
                log.info("简历解析等待重试补偿：重新投递了 {} 个任务", retryReEnqueued);
            }
        } catch (Exception e) {
            log.error("简历解析僵尸任务扫描失败，可能造成任务延迟恢复", e);
            schedulerMetrics.recordFailure("resume_parse_zombie_scan");
        }
    }
}
