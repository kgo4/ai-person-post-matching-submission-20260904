package com.example.matching.event.listener;

import com.example.matching.event.PostEmergingConfirmedEvent;
import com.example.matching.service.closure.CapabilityClosureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostEmergingConfirmedListener {

    private final CapabilityClosureService capabilityClosureService;

    /** Keep post confirmation responsive; evidence/RAG/graph closure runs after commit. */
    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(PostEmergingConfirmedEvent event) {
        try {
            capabilityClosureService.onEmergingPostConfirmed(event.postId());
        } catch (Exception e) {
            log.warn("新兴岗位闭环异步处理失败: postId={}, error={}", event.postId(), e.getMessage(), e);
        }
    }
}
