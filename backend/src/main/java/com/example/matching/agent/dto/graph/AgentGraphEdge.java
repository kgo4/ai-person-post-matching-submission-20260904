package com.example.matching.agent.dto.graph;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图谱子图边。
 * <p>
 * 边类型：HAS_ABILITY / HAS_ABILITY_FACT / REQUIRES / SUPPORTED_BY / BELONGS_TO_DOMAIN /
 * HAS_KNOWLEDGE_NODE / PREREQUISITE_OF / ASKED_IN / ANSWERED_BY / FOLLOWED_UP_BY
 */
@Data
public class AgentGraphEdge {

    /** 源节点键 */
    private String sourceNodeKey;

    /** 目标节点键 */
    private String targetNodeKey;

    /** 边类型 */
    private String edgeType;

    /** 权重 */
    private BigDecimal weight;

    /** 扩展属性 */
    private Map<String, Object> properties = new LinkedHashMap<>();

    /** 支撑该关系的来源引用（如 fact:EVIDENCE:5） */
    private List<String> sourceRefs = new ArrayList<>();

    public static AgentGraphEdge of(String sourceNodeKey, String targetNodeKey, String edgeType) {
        AgentGraphEdge edge = new AgentGraphEdge();
        edge.setSourceNodeKey(sourceNodeKey);
        edge.setTargetNodeKey(targetNodeKey);
        edge.setEdgeType(edgeType);
        return edge;
    }
}
