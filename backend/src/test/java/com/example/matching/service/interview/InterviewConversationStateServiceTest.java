package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewConversationState;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewConversationStateServiceTest {

    @Test
    void rejectsTransitionWhenPersistedStateOrVersionIsStale() {
        EmpVideoInterviewSessionMapper mapper = mock(EmpVideoInterviewSessionMapper.class);
        EmpVideoInterviewSession current = new EmpVideoInterviewSession();
        current.setId(42L);
        current.setConversationState(InterviewConversationState.PRESET_QUESTION.name());
        current.setSessionVersion(3L);
        when(mapper.selectById(42L)).thenReturn(current);
        when(mapper.compareAndSetConversationState(
                42L,
                InterviewConversationState.PRESET_QUESTION.name(),
                3L,
                InterviewConversationState.ANSWERING_PRESET.name())).thenReturn(0);

        InterviewConversationStateService service = new InterviewConversationStateService(mapper);

        assertThat(service.transition(
                42L,
                InterviewConversationState.PRESET_QUESTION,
                InterviewConversationState.ANSWERING_PRESET)).isFalse();
        assertThat(service.getState(42L)).isEqualTo(InterviewConversationState.PRESET_QUESTION);
    }
}
