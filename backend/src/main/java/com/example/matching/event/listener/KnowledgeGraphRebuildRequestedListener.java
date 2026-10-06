package com.example.matching.event.listener;

import com.example.matching.event.KnowledgeGraphRebuildRequestedEvent;
import com.example.matching.service.kg.GraphBuildTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 图谱全量重建请求监听器。
 * <p>
 * M28：提交后（AFTER_COMMIT）处理，避免"请求失败但标签/模型实际已保存"的不可观测状态；
 * 异常只记录指标（不影响业务事务）；图变更请求本身由 GraphBuildTaskService.requestFullRebuild
 * 持久化到 KgGraphBuildTask 表 + Outbox，提交后的失败可重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeGraphRebuildRequestedListener {

    private final GraphBuildTaskService graphBuildTaskService;

    private static final AtomicLong HANDLER_FAILURES = new AtomicLong(0);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(KnowledgeGraphRebuildRequestedEvent event) {
        try {
            graphBuildTaskService.requestFullRebuild(null);
        } catch (Exception e) {
            long failures = HANDLER_FAILURES.incrementAndGet();
            log.error("[LISTENER_ERROR] 图重建请求处理失败（图变更请求已持久化，可重试）: totalFailures={}, errorType={}",
                    failures, e.getClass().getSimpleName());
        }
    }
}
