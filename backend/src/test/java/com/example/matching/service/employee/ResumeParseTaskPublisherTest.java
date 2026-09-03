package com.example.matching.service.employee;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.event.ResumeParseQueuedEvent;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.employee.support.ResumeParseTaskPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResumeParseTaskPublisherTest {

    @Mock private EventOutboxDispatcher outboxDispatcher;

    @Test
    void enqueuesResumeParseToOutboxBeforeCommit() {
        ResumeParseTaskPublisher publisher = new ResumeParseTaskPublisher(outboxDispatcher);

        publisher.publishBeforeCommit(new ResumeParseQueuedEvent(42L));

        verify(outboxDispatcher).enqueue(
                eq("RESUME_PARSE"),
                eq(RabbitMQConfig.MATCHING_EXCHANGE),
                eq("resume.parse.execute"),
                eq(42L));
    }
}
