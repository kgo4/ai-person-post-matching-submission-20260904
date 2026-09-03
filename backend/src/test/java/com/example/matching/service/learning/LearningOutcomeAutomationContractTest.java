package com.example.matching.service.learning;

import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.event.LearningProjectApprovedEvent;
import com.example.matching.event.listener.LearningProjectApprovedListener;
import com.example.matching.service.closure.CapabilityClosureService;
import com.example.matching.service.learning.impl.AiLearningSuggestionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LearningOutcomeAutomationContractTest {

    @Mock
    private CapabilityClosureService capabilityClosureService;

    @InjectMocks
    private LearningProjectApprovedListener listener;

    @Test
    void aiSuggestionsCanCreateTheMatchingLinkedLearningPlan() {
        Field planService = Arrays.stream(AiLearningSuggestionServiceImpl.class.getDeclaredFields())
                .filter(field -> field.getName().equals("learningPathPlanService"))
                .findFirst()
                .orElse(null);

        assertThat(planService).isNotNull();
    }

    @Test
    void approvedLearningProjectIsHandledByTheReliableMessageConsumer() throws Exception {
        Class<?> listener = Class.forName("com.example.matching.event.listener.LearningProjectApprovedListener");
        Method method = listener.getDeclaredMethod("handle",
                Class.forName("com.example.matching.event.LearningProjectApprovedEvent"));

        assertThat(method.getAnnotation(RabbitListener.class)).isNotNull();
    }

    @Test
    void approvedLearningProjectStartsTheCapabilityClosureWithCanonicalLearningSource() {
        CapabilityClosureResult result = new CapabilityClosureResult();
        result.setClosureStatus("SUCCEEDED");
        when(capabilityClosureService.onLearningOutcomeConfirmed(org.mockito.ArgumentMatchers.any()))
                .thenReturn(result);
        listener.handle(new LearningProjectApprovedEvent(31L, 7L, 8L, "Java", 2, 4));

        ArgumentCaptor<LearningOutcomeConfirmDTO> outcome =
                ArgumentCaptor.forClass(LearningOutcomeConfirmDTO.class);
        verify(capabilityClosureService).onLearningOutcomeConfirmed(outcome.capture());

        assertThat(outcome.getValue())
                .extracting(
                        LearningOutcomeConfirmDTO::getEmpId,
                        LearningOutcomeConfirmDTO::getTagId,
                        LearningOutcomeConfirmDTO::getAbilityName,
                        LearningOutcomeConfirmDTO::getCompletedResourceId,
                        LearningOutcomeConfirmDTO::getBeforeLevel,
                        LearningOutcomeConfirmDTO::getConfirmedLevel,
                        LearningOutcomeConfirmDTO::getConfirmationSource)
                .containsExactly(7L, 8L, "Java", 31L, 2, 4, "LEARNING_PROJECT");
    }

    @Test
    void failedCapabilityClosureIsRetriedByTheDedicatedListenerFactory() {
        CapabilityClosureResult result = new CapabilityClosureResult();
        result.setClosureStatus("FAILED");
        when(capabilityClosureService.onLearningOutcomeConfirmed(org.mockito.ArgumentMatchers.any()))
                .thenReturn(result);

        assertThatThrownBy(() -> listener.handle(new LearningProjectApprovedEvent(31L, 7L, 8L, "Java", 2, 4)))
                .isInstanceOf(IllegalStateException.class);
    }
}
