package com.example.matching.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agent运行结果基础DTO
 *
 * @author system
 */
@Data
public class AgentRunResult {
    /** 原始模型输出 */
    private String rawModelOutput;

    /** 是否使用了降级方案 */
    private Boolean fallbackUsed;

    /** 来源引用列表 */
    private List<AgentSourceRef> sourceRefs;

    /**
     * Overall run confidence (0-100), calculated as weighted mean of non-null
     * sourceRef confidence scores. Null when no evidence/source references exist.
     */
    private BigDecimal overallConfidence;
}
