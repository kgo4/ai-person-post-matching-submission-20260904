package com.example.matching.listener;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.event.GraphBuildQueuedEvent;
import com.example.matching.service.kg.GraphBuildTaskService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GraphBuildTaskListener {

    private final GraphBuildTaskService graphBuildTaskService;

    @RabbitListener(queues = RabbitMQConfig.KG_GRAPH_BUILD_TASK_QUEUE, containerFactory = "slowRabbitListenerContainerFactory")
    public void handle(GraphBuildQueuedEvent event) {
        SecurityUtils.setSystemContext();
        try {
            graphBuildTaskService.executeQueuedTask(event.taskCode());
        } finally {
            SecurityUtils.clear();
        }
    }
}
