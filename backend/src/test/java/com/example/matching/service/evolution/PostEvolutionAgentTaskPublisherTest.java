package com.example.matching.service.evolution;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.dto.evolution.PostEvolutionAgentRequest;
import com.example.matching.event.PostEvolutionAgentQueuedEvent;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.evolution.support.PostEvolutionAgentTaskPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostEvolutionAgentTaskPublisherTest {

    @Mock private EventOutboxDispatcher outboxDispatcher;

    @Test
    void enqueuesAgentTaskToOutboxAfterTransactionCommit() {
        PostEvolutionAgentTaskPublisher publisher = new PostEvolutionAgentTaskPublisher(outboxDispatcher);
        PostEvolutionAgentRequest request = new PostEvolutionAgentRequest();
        request.setPostId(7L);
        PostEvolutionAgentQueuedEvent event = new PostEvolutionAgentQueuedEvent(51L, request);

        publisher.publishBeforeCommit(event);

        verify(outboxDispatcher).enqueue(
                eq("POST_EVOLUTION_AGENT"),
                eq(RabbitMQConfig.MATCHING_EXCHANGE),
                eq("post.evolution.agent.execute"),
                eq(event));
    }
}
