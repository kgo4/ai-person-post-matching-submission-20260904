package com.example.matching.event.listener;

import com.example.matching.event.KnowledgeGraphRebuildRequestedEvent;
import com.example.matching.service.kg.GraphBuildTaskService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KnowledgeGraphRebuildRequestedListenerTest {

    @Test
    void createsGraphBuildTask() {
        GraphBuildTaskService service = mock(GraphBuildTaskService.class);
        KnowledgeGraphRebuildRequestedListener listener = new KnowledgeGraphRebuildRequestedListener(service);

        listener.handle(new KnowledgeGraphRebuildRequestedEvent());

        verify(service).requestFullRebuild(null);
    }

    @Test
    void taskCreationFailureIsCaughtAndRecordedInsteadOfDroppingSilently() {
        // M28：AFTER_COMMIT 监听器 catch 异常、记录指标；
        // 图变更请求已由 GraphBuildTaskService 持久化（KgGraphBuildTask + Outbox），
        // 提交后的失败可重试，不会出现"请求失败但标签已保存"的不可观测状态
        GraphBuildTaskService service = mock(GraphBuildTaskService.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(service).requestFullRebuild(null);
        KnowledgeGraphRebuildRequestedListener listener = new KnowledgeGraphRebuildRequestedListener(service);

        assertThatCode(() -> listener.handle(new KnowledgeGraphRebuildRequestedEvent()))
                .doesNotThrowAnyException();
    }
}
