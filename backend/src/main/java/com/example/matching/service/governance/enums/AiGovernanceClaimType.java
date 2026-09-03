package com.example.matching.service.governance.enums;

/**
 * AI 治理声明类型枚举
 * <p>
 * 标准化 AI 生成内容的对象类型，用于判断人工采纳后的业务写入目标。
 *
 * @author system
 */
public enum AiGovernanceClaimType {

    /** 人员能力 */
    EMP_ABILITY("人员能力"),
    /** 岗位能力 */
    POST_ABILITY("岗位能力"),
    /** 能力标签 */
    ABILITY_TAG("能力标签"),
    /** 匹配解释 */
    MATCH_EXPLANATION("匹配解释"),
    /** 匹配差距诊断 */
    MATCH_GAP_DIAGNOSIS("匹配差距诊断"),
    /** 学习建议 */
    LEARNING_SUGGESTION("学习建议"),
    /** 岗位权重 */
    POST_WEIGHT("岗位权重"),
    /** 岗位演化项 */
    POST_EVOLUTION_ITEM("岗位演化项"),
    /** 证据声明 */
    EVIDENCE_CLAIM("证据声明");

    private final String label;

    AiGovernanceClaimType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
