package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpVideoInterviewSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewSessionRecoveryStateTest {

    @Test
    void storesDurableRecoveryProgress() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 14, 10, 0);
        LocalDateTime deadlineAt = startedAt.plusSeconds(60);
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();

        session.setCurrentQuestionOrder(2);
        session.setQuestionStartedAt(startedAt);
        session.setQuestionDeadlineAt(deadlineAt);
        session.setInterviewStartedAt(startedAt.minusMinutes(5));
        session.setSessionVersion(0L);

        assertThat(session.getCurrentQuestionOrder()).isEqualTo(2);
        assertThat(session.getQuestionStartedAt()).isEqualTo(startedAt);
        assertThat(session.getQuestionDeadlineAt()).isEqualTo(deadlineAt);
        assertThat(session.getInterviewStartedAt()).isEqualTo(startedAt.minusMinutes(5));
        assertThat(session.getSessionVersion()).isZero();
    }
}
