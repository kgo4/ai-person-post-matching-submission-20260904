package com.example.matching.service.interview;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewFollowUpRuntimeServiceTest {

    @Test
    void saveAndPushIsTransactional() throws NoSuchMethodException {
        Method method = InterviewFollowUpRuntimeService.class.getDeclaredMethod(
                "saveAndPush", com.example.matching.entity.interview.InterviewFollowUpQuestion.class);

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void followUpStateWritesAreTransactional() throws NoSuchMethodException {
        for (Method method : new Method[]{
                InterviewFollowUpRuntimeService.class.getDeclaredMethod("markAnswered", Long.class, String.class),
                InterviewFollowUpRuntimeService.class.getDeclaredMethod("markSkipped", Long.class, String.class),
                InterviewFollowUpRuntimeService.class.getDeclaredMethod("saveQualityEvaluation", Long.class, String.class)
        }) {
            assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
        }
    }
}
