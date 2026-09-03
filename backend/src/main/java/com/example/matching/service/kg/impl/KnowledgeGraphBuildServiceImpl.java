package com.example.matching.service.kg.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.kg.GraphBuildResultDTO;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.service.kg.KnowledgeGraphBuildService;
import com.example.matching.service.kg.build.GraphBuildContext;
import com.example.matching.service.kg.build.GraphEdgeProjectionService;
import com.example.matching.service.kg.build.GraphNodeProjectionService;
import com.example.matching.infrastructure.persistence.GraphRebuildTableRouter;
import com.example.matching.service.kg.build.Neo4jSnapshotSynchronizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphBuildServiceImpl implements KnowledgeGraphBuildService {

    private static final String MAIN_NODE_TABLE = "kg_graph_node";
    private static final String SHADOW_NODE_TABLE = "kg_graph_node_new";
    private static final String MAIN_EDGE_TABLE = "kg_graph_edge";
    private static final String SHADOW_EDGE_TABLE = "kg_graph_edge_new";

    private final KgGraphNodeMapper graphNodeMapper;
    private final KgGraphEdgeMapper graphEdgeMapper;

    private final GraphNodeProjectionService graphNodeProjectionService;
    private final GraphEdgeProjectionService graphEdgeProjectionService;
    private final Neo4jSnapshotSynchronizer neo4jSnapshotSynchronizer;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 非破坏性全量重建：
     * <ol>
     *   <li>把 kg_graph_node/edge 的写操作路由到影子表（DynamicTableNameInnerInterceptor）；</li>
     *   <li>清空影子表并投影新图到影子表；投影为空视为失败（fail-closed），主表不受影响；</li>
     *   <li>事务内原子交换：DELETE 主表 + INSERT..SELECT 影子表回填——任一步失败整体回滚，旧图保持完整。</li>
     * </ol>
     * 读者在事务提交前始终看到旧图（MySQL 默认 REPEATABLE READ 下无中间态）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GraphBuildResultDTO rebuildFullGraph() {
        String graphVersion = "KGV_" + System.currentTimeMillis();
        GraphBuildContext ctx = new GraphBuildContext(graphVersion,
                java.time.LocalDateTime.now().toString());
        log.info("开始全量重建知识图谱（影子表模式），版本={}", graphVersion);

        Map<String, Integer> nodeTypeCounts;
        Map<String, Integer> edgeTypeCounts;
        GraphRebuildTableRouter.begin();
        try {
            // 1. 清空影子表（写操作经动态表名路由到影子表）
            graphNodeMapper.delete(Wrappers.<KgGraphNode>lambdaQuery());
            graphEdgeMapper.delete(Wrappers.<KgGraphEdge>lambdaQuery());
            log.info("已清空影子图谱表");

            // 2. 投影到影子表
            nodeTypeCounts = graphNodeProjectionService.projectNodes(ctx);
            edgeTypeCounts = graphEdgeProjectionService.projectEdges(ctx);
        } finally {
            // 3. 任何异常都必须恢复路由，避免泄漏到其他请求
            GraphRebuildTableRouter.end();
        }

        int totalNodes = nodeTypeCounts.values().stream().mapToInt(Integer::intValue).sum();
        int totalEdges = edgeTypeCounts.values().stream().mapToInt(Integer::intValue).sum();
        log.info("影子表投影完成：节点={}, 边={}", totalNodes, totalEdges);

        // 4. 投影结果校验：节点或边为空视为投影失败，中止交换，主图保持原样
        if (totalNodes <= 0) {
            throw new IllegalStateException("全量重建投影节点数为 0，构建中止，主图未受影响");
        }
        if (totalEdges <= 0) {
            throw new IllegalStateException("全量重建投影边数为 0，构建中止，主图未受影响");
        }

        // 5. 事务内原子交换：DELETE 主表 + INSERT..SELECT 回填（回滚时主图完整）
        long swapStart = System.currentTimeMillis();
        int swappedNodes = swapTable(MAIN_NODE_TABLE, SHADOW_NODE_TABLE);
        int swappedEdges = swapTable(MAIN_EDGE_TABLE, SHADOW_EDGE_TABLE);
        log.info("图谱原子交换完成：节点={}, 边={}, costMs={}",
                swappedNodes, swappedEdges, System.currentTimeMillis() - swapStart);

        GraphBuildResultDTO result = GraphBuildResultDTO.success(totalNodes, totalEdges,
                nodeTypeCounts, edgeTypeCounts);
        result.setGraphVersion(graphVersion);
        // 修复：Neo4j 同步原在事务提交前执行，事务回滚会导致 MySQL/Neo4j 双写不一致。
        // 改为事务提交后（afterCommit）再同步；无事务上下文时直接同步。
        Runnable syncTask = () -> {
            try {
                // M20: Neo4j 仅用于展示/探索；同步失败由同步器入队重试任务（Outbox），可重试可查看状态
                neo4jSnapshotSynchronizer.syncIfAvailable(graphVersion == null
                        ? null : Long.valueOf(System.currentTimeMillis()));
            } catch (Exception e) {
                log.warn("Neo4j 展示图同步失败（MySQL 权威图不受影响），已记录重试任务: graphVersion={}, error={}",
                        graphVersion, e.getMessage());
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            syncTask.run();
                        }
                    });
        } else {
            syncTask.run();
        }
        log.info("知识图谱全量重建完成：节点={}, 边={}", totalNodes, totalEdges);
        return result;
    }

    private int swapTable(String mainTable, String shadowTable) {
        jdbcTemplate.update("DELETE FROM " + mainTable);
        return jdbcTemplate.update("INSERT INTO " + mainTable + " SELECT * FROM " + shadowTable);
    }
}
