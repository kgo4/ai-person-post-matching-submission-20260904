package com.example.matching.application.interview;

import com.example.matching.service.interview.InterviewConversationStateService;
import com.example.matching.service.interview.InterviewFollowUpRuntimeService;
import com.example.matching.service.interview.InterviewSessionManager;
import com.example.matching.service.interview.InterviewTranscriptBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewWebSocketSessionFacade")
class InterviewWebSocketSessionFacadeTest {

    @Mock private InterviewSessionManager manager;
    @Mock private InterviewConversationStateService conversationStateService;
    @Mock private InterviewFollowUpRuntimeService followUpRuntimeService;
    @Mock private InterviewTranscriptBuffer transcriptBuffer;

    @Test
    @DisplayName("facade delegates all three handler operations and maps resume state")
    void delegatesSessionCommandsAndMapsResumeState() throws Exception {
        com.example.matching.service.interview.InterviewResumeState state =
                new com.example.matching.service.interview.InterviewResumeState(
                        "ANSWERING", 2, "Question", 3L, 1, "Follow up", 123L, 60, 40, 9L);
        when(manager.recoverActiveSession(8L)).thenReturn(state);

        InterviewWebSocketSessionFacade facade = new InterviewWebSocketSessionFacade(
                manager, conversationStateService, followUpRuntimeService, transcriptBuffer);

        facade.startAnswerPeriodAfterQuestionRead(8L, 2, 3L);
        facade.finishInterview(8L);
        InterviewWebSocketSessionFacade.ResumeState response = facade.recoverActiveSession(8L);

        verify(manager).startAnswerPeriodAfterQuestionRead(8L, 2, 3L);
        verify(manager).finishInterview(8L);
        assertThat(response.questionText()).isEqualTo("Question");
        assertThat(response.remainingSeconds()).isEqualTo(40);
        assertThat(response.conversationState()).isEqualTo("ANSWERING");
        assertThat(response.questionOrder()).isEqualTo(2);
        assertThat(response.followUpId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("结束会话时刷新会话和当前题目的转录")
    void flushTranscriptSessionFlushesTheWholeSession() {
        InterviewWebSocketSessionFacade facade = new InterviewWebSocketSessionFacade(
                manager, conversationStateService, followUpRuntimeService, transcriptBuffer);

        facade.flushTranscriptSession(8L);

        verify(transcriptBuffer).flushSession(8L);
        verify(transcriptBuffer, never()).flush(8L);
    }
}
