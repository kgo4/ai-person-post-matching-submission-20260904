package com.example.matching.listener;

import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.event.AiTestQuestionsGeneratedEvent;
import com.example.matching.event.AiTestQuestionsGenerationFailedEvent;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AI 测试工作流进度监听器：只转发生命周期事件，不再直接推进工作流状态。
 */
class AiTestWorkflowProgressListenerTest {

    @Test
    void publishesSucceededEventWhenQuestionsGenerated() {
        CapabilityStageLifecycleEventPublisher publisher = mock(CapabilityStageLifecycleEventPublisher.class);
        AiTestWorkflowProgressListener listener = new AiTestWorkflowProgressListener(publisher);

        listener.onQuestionsGenerated(new AiTestQuestionsGeneratedEvent(9L, 88L));

        ArgumentCaptor<CapabilityStageLifecycleEvent> captor = ArgumentCaptor.forClass(CapabilityStageLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        CapabilityStageLifecycleEvent event = captor.getValue();
        assertEquals(88L, event.workflowId());
        assertEquals(9L, event.sourceRefId());
        assertEquals("AI_TEST", event.sourceRefType());
        assertEquals("AI_TEST_GENERATION", event.stageType());
        assertEquals(StageLifecycleEventType.TASK_SUCCEEDED, event.eventType());
    }

    @Test
    void skipsNonWorkflowTest() {
        CapabilityStageLifecycleEventPublisher publisher = mock(CapabilityStageLifecycleEventPublisher.class);
        AiTestWorkflowProgressListener listener = new AiTestWorkflowProgressListener(publisher);

        listener.onQuestionsGenerated(new AiTestQuestionsGeneratedEvent(9L, null));

        verify(publisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishesFailedFinalEventWhenGenerationFinalFailure() {
        CapabilityStageLifecycleEventPublisher publisher = mock(CapabilityStageLifecycleEventPublisher.class);
        AiTestWorkflowProgressListener listener = new AiTestWorkflowProgressListener(publisher);

        listener.onQuestionsGenerationFailed(
                new com.example.matching.event.AiTestQuestionsGenerationFailedEvent(9L, 88L, "题目格式不合法"));

        ArgumentCaptor<CapabilityStageLifecycleEvent> captor = ArgumentCaptor.forClass(CapabilityStageLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        CapabilityStageLifecycleEvent event = captor.getValue();
        assertEquals(StageLifecycleEventType.TASK_FAILED_FINAL, event.eventType());
        assertEquals("AI_TEST_GENERATION_FAILED", event.errorCode());
        assertEquals("题目格式不合法", event.errorMessage());
        assertNull(event.stageRunId());
    }
}
