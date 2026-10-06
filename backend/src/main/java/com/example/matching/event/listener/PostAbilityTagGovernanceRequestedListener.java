package com.example.matching.event.listener;

import com.example.matching.event.PostAbilityTagGovernanceRequestedEvent;
import com.example.matching.service.system.PostAbilityTagGovernanceService;
import com.example.matching.infrastructure.llm.AiProviderConcurrencyGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 后台执行岗位能力到系统标签库的旁路治理。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostAbilityTagGovernanceRequestedListener {

    private final PostAbilityTagGovernanceService governanceService;
    private final AiProviderConcurrencyGate providerConcurrencyGate;

    @Async("abilityTagGovernanceExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(PostAbilityTagGovernanceRequestedEvent event) {
        try {
            providerConcurrencyGate.executeInBackground(() -> {
                governanceService.govern(event);
                return null;
            });
        } catch (Exception e) {
            log.warn("岗位能力标签旁路治理失败，不影响岗位能力表和全景图谱: postId={}, ability={}, error={}",
                    event == null ? null : event.postId(), event == null ? null : event.abilityName(), e.getMessage());
        }
    }
}
