package com.example.matching.service.kg;

import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;

import java.util.List;
import java.util.Map;

public interface Neo4jGraphStore {

    Map<String, Object> health();

    /** 查询图谱展示数据；Neo4j 不可用或无数据时由上层回退 MySQL。 */
    Map<String, Object> queryPanorama(List<String> nodeTypes, String keyword, String category, int limit);

    Map<String, Object> syncSnapshot(List<KgGraphNode> nodes, List<KgGraphEdge> edges);

    Map<String, Object> syncIncremental(List<KgGraphNode> upsertNodes, List<KgGraphEdge> upsertEdges,
                                        List<String> deletedNodeKeys, List<String> deletedEdgeKeys);
}
