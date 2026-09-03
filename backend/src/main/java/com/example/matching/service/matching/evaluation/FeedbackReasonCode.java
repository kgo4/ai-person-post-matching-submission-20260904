package com.example.matching.service.matching.evaluation;

/**
 * 反馈原因代码枚举
 * <p>
 * 人工复核时必须选择一个原因代码，用于结构化校准数据导出。
 * 每个代码关联到特定的评分维度。
 */
public enum FeedbackReasonCode {

    // ===== hardConditionScore 维度 =====
    /** 系统标记硬条件不通过，但复核人确认应通过 */
    HARD_CONDITION_FALSE_FAIL("hardConditionScore", "系统误判硬条件不通过"),
    /** 系统标记硬条件通过，但复核人确认应不通过 */
    HARD_CONDITION_FALSE_PASS("hardConditionScore", "系统误判硬条件通过"),

    // ===== abilityScore 维度 =====
    /** 能力模型分偏低 */
    ABILITY_UNDERESTIMATED("abilityScore", "能力模型分偏低"),
    /** 能力模型分偏高 */
    ABILITY_OVERESTIMATED("abilityScore", "能力模型分偏高"),

    // ===== semanticScore 维度 =====
    /** 向量语义相似度偏高 */
    SEMANTIC_RECALL_TOO_HIGH("semanticScore", "向量语义相似度偏高"),
    /** 向量语义相似度偏低 */
    SEMANTIC_RECALL_TOO_LOW("semanticScore", "向量语义相似度偏低"),

    // ===== evidenceScore 维度 =====
    /** 证据置信度应更低 */
    EVIDENCE_TOO_WEAK("evidenceScore", "证据置信度应更低"),
    /** 来源可信度不可靠 */
    EVIDENCE_SOURCE_UNRELIABLE("evidenceScore", "来源可信度不可靠"),

    // ===== modelQualityScore 维度 =====
    /** 岗位模型质量实质影响了评分 */
    MODEL_QUALITY_AFFECTED("modelQualityScore", "岗位模型质量影响评分"),

    // ===== llmScore 维度 =====
    /** LLM评分或推理不准确 */
    LLM_REASONING_INACCURATE("llmScore", "LLM评分或推理不准确"),

    // ===== 通用 =====
    /** 其他人工复核修正 */
    OTHER_MANUAL_REVIEW("any", "其他人工复核修正");

    private final String dimensionKey;
    private final String description;

    FeedbackReasonCode(String dimensionKey, String description) {
        this.dimensionKey = dimensionKey;
        this.description = description;
    }

    public String getDimensionKey() {
        return dimensionKey;
    }

    public String getDescription() {
        return description;
    }
}
