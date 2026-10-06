package com.example.matching.service.governance.enums;

/**
 * AI 治理场景枚举
 * <p>
 * 标准化所有 AI 生成内容的业务场景，用于 Harness 审核和治理记录筛选。
 *
 * @author system
 */
public enum AiGovernanceScenario {

    /** 简历解析 - 人员能力提取 */
    EMP_ABILITY_RESUME_PARSE("简历解析"),
    /** AI 测评 - 人员能力提取 */
    EMP_ABILITY_AI_TEST("AI测评"),
    /** 视频面试 - 人员能力提取 */
    EMP_ABILITY_VIDEO_INTERVIEW("视频面试"),
    /** PMS 分析 - 人员能力提取 */
    EMP_ABILITY_PMS_ANALYSIS("PMS分析"),
    /** JD 能力提取 */
    POST_ABILITY_JD_EXTRACT("JD能力提取"),
    /** 岗位能力生成 */
    POST_ABILITY_GENERATION("岗位能力生成"),
    /** 岗位演化 */
    POST_EVOLUTION("岗位演化"),
    /** 公司岗位权重 */
    COMPANY_POST_WEIGHT("公司岗位权重"),
    /** 匹配差距诊断 */
    MATCH_GAP_DIAGNOSIS("匹配差距诊断"),
    /** 匹配解释 */
    MATCH_EXPLANATION("匹配解释"),
    /** 学习路径建议 */
    LEARNING_PATH_SUGGESTION("学习路径建议"),
    /** 能力标签治理 */
    ABILITY_TAG_GOVERNANCE("能力标签治理"),
    /** 证据治理 */
    EVIDENCE_GOVERNANCE("证据治理");

    private final String label;

    AiGovernanceScenario(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
