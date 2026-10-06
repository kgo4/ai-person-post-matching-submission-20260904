package com.example.matching.application.interview;

import com.example.matching.entity.interview.InterviewConversationState;
import com.example.matching.service.interview.InterviewConversationStateService;
import com.example.matching.service.interview.InterviewFollowUpRuntimeService;
import com.example.matching.service.interview.InterviewResumeState;
import com.example.matching.service.interview.InterviewSessionManager;
import com.example.matching.service.interview.InterviewTranscriptBuffer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterviewWebSocketSessionFacade {
    private final InterviewSessionManager interviewSessionManager;
    private final InterviewConversationStateService conversationStateService;
    private final InterviewFollowUpRuntimeService followUpRuntimeService;
    private final InterviewTranscriptBuffer transcriptBuffer;

    public void startAnswerPeriodAfterQuestionRead(Long sessionId, Integer questionOrder, Long followUpId)
            throws Exception {
        interviewSessionManager.startAnswerPeriodAfterQuestionRead(sessionId, questionOrder, followUpId);
    }

    public void finishInterview(Long sessionId) throws Exception {
        interviewSessionManager.finishInterview(sessionId);
    }

    public ResumeState recoverActiveSession(Long sessionId) throws Exception {
        InterviewResumeState state = interviewSessionManager.recoverActiveSession(sessionId);
        return state == null ? null : new ResumeState(
                state.conversationState(), state.questionOrder(), state.questionText(), state.followUpId(),
                state.followUpOrder(), state.followUpQuestionText(), state.questionDeadlineEpochMillis(),
                state.durationSeconds(), state.remainingSeconds(), state.sessionVersion());
    }

    public InterviewConversationState getConversationState(Long sessionId) {
        return conversationStateService.getState(sessionId);
    }

    public void appendFollowUpTranscript(Long sessionId, String text) {
        followUpRuntimeService.appendFollowUpTranscript(sessionId, text);
    }

    public void appendTranscript(Long sessionId, String text) {
        transcriptBuffer.append(sessionId, text);
    }

    public void flushTranscriptSession(Long sessionId) {
        transcriptBuffer.flushSession(sessionId);
    }

    public void setTranscriptCurrentQuestion(Long sessionId, Long questionId) {
        transcriptBuffer.setCurrentQuestion(sessionId, questionId);
    }

    public void appendTranscriptCurrentQuestion(Long sessionId, String text) {
        transcriptBuffer.appendCurrentQuestion(sessionId, text);
    }

    /**
     * 会话是否允许 START（status=1）。
     */
    public boolean canStartInterview(Long sessionId) {
        return interviewSessionManager.isStartable(sessionId);
    }

    /**
     * 会话是否进行中（status=2）。
     */
    public boolean isActiveSession(Long sessionId) {
        return interviewSessionManager.isActive(sessionId);
    }

    /**
     * 按题目序号解析题目 ID。
     */
    public Long resolveQuestionId(Long sessionId, int questionOrder) {
        return interviewSessionManager.resolveQuestionId(sessionId, questionOrder);
    }

    public record ResumeState(
            String conversationState, Integer questionOrder, String questionText, Long followUpId,
            Integer followUpOrder, String followUpQuestionText, long questionDeadlineEpochMillis,
            int durationSeconds, int remainingSeconds, long sessionVersion) {
    }
}
