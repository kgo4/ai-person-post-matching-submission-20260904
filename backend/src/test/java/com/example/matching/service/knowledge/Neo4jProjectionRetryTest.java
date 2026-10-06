package com.example.matching.service.knowledge;

import com.example.matching.entity.common.KnowledgeProjectionTask;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.service.kg.Neo4jGraphStore;
import com.example.matching.service.kg.build.Neo4jSnapshotSynchronizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M20 行为测试：Neo4j 展示图同步失败不静默——
 * 记录 NEO4J_GRAPH 投影任务到 Outbox，由 worker 重试并可查看状态；
 * MySQL 权威图查询不受影响。
 */
class Neo4jProjectionRetryTest {

    @Test
    void syncFailureEnqueuesNeo4jProjectionTask() {
        Neo4jGraphStore graphStore = mock(Neo4jGraphStore.class);
        ObjectProvider<Neo4jGraphStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(graphStore);
        when(graphStore.syncSnapshot(anyList(), anyList()))
                .thenThrow(new RuntimeException("neo4j connection refused"));
        @SuppressWarnings("unchecked")
        ObjectProvider<Neo4jGraphStore> typedProvider = provider;
        KgGraphNodeMapper nodeMapper = mock(KgGraphNodeMapper.class);
        KgGraphEdgeMapper edgeMapper = mock(KgGraphEdgeMapper.class);
        when(nodeMapper.selectList(any())).thenReturn(List.of());
        when(edgeMapper.selectList(any())).thenReturn(List.of());
        com.example.matching.port.knowledge.KnowledgeProjectionPort projectionPort =
                mock(com.example.matching.port.knowledge.KnowledgeProjectionPort.class);

        Neo4jSnapshotSynchronizer synchronizer = new Neo4jSnapshotSynchronizer(
                typedProvider, nodeMapper, edgeMapper, projectionPort);

        synchronizer.syncIfAvailable(42L);

        // 失败已入队重试任务（Outbox 记录、可查看状态）
        verify(projectionPort).enqueueNeo4jGraphSnapshot(42L);
    }

    @Test
    void workerRetriesNeo4jProjectionAndMarksSucceeded() {
        KnowledgeProjectionTask task = new KnowledgeProjectionTask();
        task.setId(9L);
        task.setProjection(KnowledgeProjectionTask.Projection.NEO4J_GRAPH.name());
        task.setAggregateType("GRAPH_SNAPSHOT");
        task.setAggregateId(42L);
        KnowledgeProjectionTaskService projectionTaskService = mock(KnowledgeProjectionTaskService.class);
        when(projectionTaskService.claimNextBatch(KnowledgeProjectionTask.Projection.NEO4J_GRAPH, 20))
                .thenReturn(List.of(task));

        Neo4jGraphStore graphStore = mock(Neo4jGraphStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Neo4jGraphStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(graphStore);
        when(graphStore.syncSnapshot(anyList(), anyList())).thenReturn(java.util.Map.of("nodes", 1));
        KgGraphNodeMapper nodeMapper = mock(KgGraphNodeMapper.class);
        KgGraphEdgeMapper edgeMapper = mock(KgGraphEdgeMapper.class);
        when(nodeMapper.selectList(any())).thenReturn(List.of(new KgGraphNode()));
        when(edgeMapper.selectList(any())).thenReturn(List.of(new KgGraphEdge()));
        Neo4jSnapshotSynchronizer synchronizer = new Neo4jSnapshotSynchronizer(
                provider, nodeMapper, edgeMapper,
                mock(com.example.matching.port.knowledge.KnowledgeProjectionPort.class));
        KnowledgeProjectionWorker worker = new KnowledgeProjectionWorker(
                projectionTaskService, mock(com.example.matching.service.rag.KnowledgeDocumentService.class),
                synchronizer);

        worker.projectNeo4jGraphSnapshot();

        verify(projectionTaskService).markSucceeded(9L);
    }
}
