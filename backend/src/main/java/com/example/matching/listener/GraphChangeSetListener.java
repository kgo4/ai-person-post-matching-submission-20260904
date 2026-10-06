package com.example.matching.listener;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.event.GraphChangeSetQueuedEvent;
import com.example.matching.service.kg.GraphChangeSetService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GraphChangeSetListener {

    private final GraphChangeSetService graphChangeSetService;

    @RabbitListener(queues = RabbitMQConfig.KG_GRAPH_CHANGE_SET_QUEUE, containerFactory = "fastRabbitListenerContainerFactory")
    public void handle(GraphChangeSetQueuedEvent event) {
        SecurityUtils.setSystemContext();
        try {
            graphChangeSetService.executeChange(event.changeCode());
        } finally {
            SecurityUtils.clear();
        }
    }
}
