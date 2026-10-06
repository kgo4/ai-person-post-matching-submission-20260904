package com.example.matching.service.kg.build;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.service.kg.Neo4jGraphStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jSnapshotSynchronizer {

    private final ObjectProvider<Neo4jGraphStore> neo4jGraphStoreProvider;
    private final KgGraphNodeMapper graphNodeMapper;
    private final KgGraphEdgeMapper graphEdgeMapper;
    private final com.example.matching.port.knowledge.KnowledgeProjectionPort projectionPort;

    public void syncIfAvailable() {
        syncIfAvailable(null);
    }

    /**
     * 同步 MySQL 权威图投影到 Neo4j（仅展示/探索）。
     * <p>
     * M20：同步失败不静默——记录 NEO4J_GRAPH 投影任务到 Outbox，由
     * KnowledgeProjectionWorker 重试（最多 10 次），可查看状态。
     *
     * @param graphVersion 当前图版本（用于重试任务溯源），可为 null
     */
    public void syncIfAvailable(Long graphVersion) {
        try {
            Neo4jGraphStore graphStore = neo4jGraphStoreProvider.getIfAvailable();
            if (graphStore == null) {
                log.debug("Neo4j 未配置（neo4j.graph.enabled=false），跳过展示图同步");
                return;
            }
            List<KgGraphNode> nodes = graphNodeMapper.selectList(Wrappers.<KgGraphNode>lambdaQuery());
            List<KgGraphEdge> edges = graphEdgeMapper.selectList(Wrappers.<KgGraphEdge>lambdaQuery());
            Map<String, Object> result = graphStore.syncSnapshot(nodes, edges);
            log.info("Neo4j graph sync result: {}", result);
        } catch (Exception e) {
            // M20：失败可重试——入队 NEO4J_GRAPH 投影任务（Outbox），由 worker 重试并可查看状态
            long version = graphVersion != null ? graphVersion : System.currentTimeMillis();
            try {
                projectionPort.enqueueNeo4jGraphSnapshot(version);
                log.warn("Neo4j 展示图同步失败，已记录重试任务: graphVersion={}, error={}",
                        version, e.getMessage());
            } catch (Exception enqueueEx) {
                log.error("Neo4j 同步失败且重试任务入队失败: error={}, enqueueError={}",
                        e.getMessage(), enqueueEx.getMessage());
            }
        }
    }
}
