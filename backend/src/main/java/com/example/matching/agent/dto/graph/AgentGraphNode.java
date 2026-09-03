package com.example.matching.agent.dto.graph;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图谱子图节点。
 * <p>
 * 节点类型：EMPLOYEE / POST / ABILITY / EVIDENCE / KNOWLEDGE_DOMAIN /
 * KNOWLEDGE_NODE / INTERVIEW_SESSION / INTERVIEW_QUESTION / INTERVIEW_FOLLOW_UP
 */
@Data
public class AgentGraphNode {

    /** 节点键，如 EMPLOYEE:12 / ABILITY:34 */
    private String nodeKey;

    /** 节点类型 */
    private String nodeType;

    /** 业务引用 ID */
    private Long refId;

    /** 节点标签 */
    private String label;

    /** 扩展属性 */
    private Map<String, Object> properties = new LinkedHashMap<>();

    public static AgentGraphNode of(String nodeKey, String nodeType, Long refId, String label) {
        AgentGraphNode node = new AgentGraphNode();
        node.setNodeKey(nodeKey);
        node.setNodeType(nodeType);
        node.setRefId(refId);
        node.setLabel(label);
        return node;
    }
}
