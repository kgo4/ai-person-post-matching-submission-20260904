package com.example.matching.event.listener;

import com.example.matching.event.GraphChangeRequestedEvent;
import com.example.matching.service.kg.GraphChangeSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GraphChangeRequestedListener {

    private final GraphChangeSetService graphChangeSetService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(GraphChangeRequestedEvent event) {
        graphChangeSetService.requestChange(event.sourceType(), event.entityType(), event.entityId(),
                event.operationType(), event.payload(), event.createdBy());
    }
}
