package com.example.matching.schedule;

import com.example.matching.service.ability.AgentMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/**
 * Agent 记忆过期清理调度器。
 * <p>
 * 每小时清理一次已过期的 agent_memory；多实例部署时通过 ScheduledTaskRunner 分布式锁保证单实例执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentMemoryExpirationScheduler {

    private final AgentMemoryService agentMemoryService;
    private final SchedulerMetrics schedulerMetrics;

    @Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Scheduled(cron = "${agent.memory.expiration-cron:0 0 * * * ?}")
    public void expireDueMemories() {
        if (taskRunner != null) {
            taskRunner.run("agent_memory_expiration", this::expireDueMemoriesInternal);
        } else {
            expireDueMemoriesInternal();
        }
    }

    private void expireDueMemoriesInternal() {
        try {
            int expired = agentMemoryService.expireDueMemories();
            if (expired > 0) {
                log.info("Expired {} due agent memories", expired);
            }
        } catch (Exception e) {
            log.error("Agent memory expiration scan failed, memories may be stale", e);
            schedulerMetrics.recordFailure("agent_memory_expiration");
        }
    }
}
