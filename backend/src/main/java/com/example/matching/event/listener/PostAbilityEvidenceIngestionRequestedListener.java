package com.example.matching.event.listener;

import com.example.matching.event.PostAbilityEvidenceIngestionRequestedEvent;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostAbilityEvidenceIngestionRequestedListener {
    private final AbilityEvidenceIngestionService abilityEvidenceIngestionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(PostAbilityEvidenceIngestionRequestedEvent event) {
        try {
            abilityEvidenceIngestionService.ingestPostAbilityModel(event.modelId(), event.sourceType());
        } catch (Exception e) {
            log.warn("岗位能力证据同步失败，不影响岗位模型: modelId={}, error={}",
                    event.modelId(), e.getMessage());
        }
    }
}
