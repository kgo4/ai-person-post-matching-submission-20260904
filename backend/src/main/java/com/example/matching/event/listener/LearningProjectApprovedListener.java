package com.example.matching.event.listener;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.event.LearningProjectApprovedEvent;
import com.example.matching.service.closure.CapabilityClosureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningProjectApprovedListener {

    private final CapabilityClosureService capabilityClosureService;

    @RabbitListener(queues = RabbitMQConfig.LEARNING_OUTCOME_CLOSURE_QUEUE,
            containerFactory = "learningOutcomeClosureRabbitListenerContainerFactory")
    public void handle(LearningProjectApprovedEvent event) {
        LearningOutcomeConfirmDTO outcome = new LearningOutcomeConfirmDTO();
        outcome.setEmpId(event.empId());
        outcome.setTagId(event.tagId());
        outcome.setAbilityName(event.abilityName());
        outcome.setCompletedResourceId(event.submissionId());
        outcome.setBeforeLevel(event.beforeLevel());
        outcome.setConfirmedLevel(event.confirmedLevel());
        outcome.setConfirmationSource("LEARNING_PROJECT");
        outcome.setNote("Approved learning project submission: " + event.submissionId());

        CapabilityClosureResult result = capabilityClosureService.onLearningOutcomeConfirmed(outcome);
        if (result == null || !"SUCCEEDED".equals(result.getClosureStatus())) {
            String status = result != null ? result.getClosureStatus() : "NO_RESULT";
            // 交由专用 listener factory 做有限重试；耗尽后才进入 DLQ，避免短暂的
            // Harness、数据库或图谱故障让已审核的学习成果永久丢失。
            throw new IllegalStateException(
                    "Learning outcome closure failed for approved submission " + event.submissionId() + ": " + status);
        }
    }
}
