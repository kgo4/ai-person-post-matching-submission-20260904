package com.example.matching.service.interview;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewResumeStateTest {

    @Test
    void calculatesRemainingSecondsFromServerDeadlineWithoutNegativeValues() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 14, 10, 0, 0);

        assertThat(InterviewResumeState.remainingSeconds(now.plusSeconds(37), now)).isEqualTo(37);
        assertThat(InterviewResumeState.remainingSeconds(now.minusSeconds(1), now)).isZero();
    }
}
