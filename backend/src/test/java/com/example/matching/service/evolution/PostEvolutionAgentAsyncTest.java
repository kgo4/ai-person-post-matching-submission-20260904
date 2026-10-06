package com.example.matching.service.evolution;

import com.example.matching.dto.evolution.PostEvolutionAgentRequest;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.event.PostEvolutionAgentQueuedEvent;
import com.example.matching.mapper.evolution.PostEvolutionChangeItemMapper;
import com.example.matching.mapper.evolution.PostEvolutionEvidenceMapper;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.PostDTO;
import com.example.matching.service.evolution.impl.PostEvolutionAgentPipeline;
import com.example.matching.service.evolution.impl.PostEvolutionAgentServiceImpl;
import com.example.matching.service.evolution.support.EvolutionAbilityTagResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PostEvolutionAgentAsyncTest {

    @Mock private PostEvolutionTaskMapper taskMapper;
    @Mock private PostEvolutionChangeItemMapper changeItemMapper;
    @Mock private PostEvolutionEvidenceMapper evidenceMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private PostQueryPort postQueryPort;
    @Mock private PostEvolutionKnowledgeRetrievalService knowledgeRetrievalService;
    @Mock private PostEvolutionSignalService signalService;
    @Mock private EvolutionHarnessOrchestrator harnessOrchestrator;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EvolutionAbilityTagResolver abilityTagResolver;
    @Mock private PostEvolutionAgentPipeline pipeline;

    @Test
    void runEvolutionAndCreateTask_queuesAgentWorkWithoutBlockingRequest() {
        PostEvolutionAgentServiceImpl service = createService();
        org.mockito.Mockito.when(postQueryPort.getPostById(7L))
                .thenReturn(new PostDTO(7L, "后端工程师", null, null, null, null, null));
        doAnswer(invocation -> {
            invocation.getArgument(0, PostEvolutionTask.class).setId(51L);
            return 1;
        }).when(taskMapper).insert(any(PostEvolutionTask.class));

        PostEvolutionAgentRequest request = new PostEvolutionAgentRequest();
        request.setPostId(7L);
        request.setOperatorId(9L);
        request.setTriggerType("MANUAL_RUN");
        request.setIncludeWhitepaper(true);
        request.setIncludeCloudKnowledge(true);

        PostEvolutionTask task = service.runEvolutionAndCreateTask(request);

        assertThat(task.getTaskStatus()).isEqualTo("PENDING");
        assertThat(task.getNewJdText()).isEmpty();
        assertThat(task.getProgressStatus()).isEqualTo("QUEUED");
        assertThat(task.getProgressPercent()).isZero();
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOf(PostEvolutionAgentQueuedEvent.class)
                .extracting(event -> ((PostEvolutionAgentQueuedEvent) event).taskId())
                .isEqualTo(51L);
        verifyNoInteractions(knowledgeRetrievalService, signalService, harnessOrchestrator);
    }

    private PostEvolutionAgentServiceImpl createService() {
        return new PostEvolutionAgentServiceImpl(
                taskMapper,
                changeItemMapper,
                evidenceMapper,
                postAbilityModelMapper,
                postQueryPort,
                knowledgeRetrievalService,
                signalService,
                harnessOrchestrator,
                new ObjectMapper(),
                eventPublisher,
                pipeline,
                abilityTagResolver);
    }
}
