package com.example.matching.service.employee;

import com.example.matching.service.employee.impl.AiTestServiceImpl;
import com.example.matching.service.employee.impl.ResumeParseServiceImpl;
import com.example.matching.service.employee.impl.VideoInterviewServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncAiWorkflowTransactionBoundaryTest {

    @Test
    void remoteAiWorkflowMethodsDoNotHoldDatabaseTransactions() throws NoSuchMethodException {
        assertThat(AiTestServiceImpl.class.getMethod("processGenerateQuestions", Long.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(AiTestServiceImpl.class.getMethod("processEvaluateAnswers", Long.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(ResumeParseServiceImpl.class.getMethod("processQueuedParse", Long.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(VideoInterviewServiceImpl.class.getMethod("analyze", Long.class)
                .getAnnotation(Transactional.class)).isNull();
    }
}
