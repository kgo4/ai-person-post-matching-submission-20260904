package com.example.matching.listener;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.event.PostEvolutionAgentQueuedEvent;
import com.example.matching.service.evolution.PostEvolutionAgentService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 岗位演化 Agent 后台任务消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostEvolutionAgentTaskListener {

    private final PostEvolutionAgentService postEvolutionAgentService;

    @RabbitListener(queues = RabbitMQConfig.POST_EVOLUTION_AGENT_TASK_QUEUE, containerFactory = "slowRabbitListenerContainerFactory")
    public void handle(PostEvolutionAgentQueuedEvent event) {
        log.info("收到岗位演化 Agent 任务消息: taskId={}", event.taskId());
        SecurityUtils.setSystemContext();
        try {
            postEvolutionAgentService.executeQueuedEvolution(event.taskId(), event.request());
        } finally {
            SecurityUtils.clear();
        }
    }
}
