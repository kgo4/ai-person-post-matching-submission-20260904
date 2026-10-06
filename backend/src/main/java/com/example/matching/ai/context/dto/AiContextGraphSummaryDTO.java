package com.example.matching.ai.context.dto;

import lombok.Data;

import java.util.List;

/**
 * AI上下文图谱摘要DTO
 *
 * @author system
 */
@Data
public class AiContextGraphSummaryDTO {

    /** 节点数量 */
    private Integer nodeCount;

    /** 边数量 */
    private Integer edgeCount;

    /** 能力节点数量 */
    private Integer abilityCount;

    /** 证据节点数量 */
    private Integer evidenceCount;

    /** 关键能力节点 */
    private List<String> keyAbilityNodes;

    /** 关键路径摘要 */
    private List<String> keyPaths;
}
