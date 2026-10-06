package com.example.matching.service.interview;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Interview snapshot sent to the client during connection recovery.
 */
public record InterviewResumeState(
        String conversationState,
        Integer questionOrder,
        String questionText,
        Long followUpId,
        Integer followUpOrder,
        String followUpQuestionText,
        long questionDeadlineEpochMillis,
        int durationSeconds,
        int remainingSeconds,
        long sessionVersion
) {

    public static int remainingSeconds(LocalDateTime deadlineAt, LocalDateTime now) {
        if (deadlineAt == null || !deadlineAt.isAfter(now)) {
            return 0;
        }
        return (int) Math.max(1, Duration.between(now, deadlineAt).toSeconds());
    }
}
