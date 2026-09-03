package com.example.matching.service.rag;

import lombok.Getter;

/**
 * RAG 场景枚举
 * <p>
 * 统一管理所有 RAG 使用场景，避免散落字符串。
 * 每个场景配置：允许检索的 sourceType、topK、最低相似度、是否允许云知识库等。
 */
@Getter
public enum RagScenarioEnum {

    /**
     * 岗位演化
     * 检索：历史JD、岗位原型、当前岗位能力模型、外部岗位趋势
     */
    POST_EVOLUTION("岗位演化", new String[]{"JD_IMPORT", "POST_PROTOTYPE", "POST_ABILITY_MODEL", "EXTERNAL_TREND", "VOLCENGINE_KB"}, 5, 0.5, true, true),

    /**
     * 匹配证据链叙事
     * 检索：岗位能力模型、能力标签、证据
     * 注意：不进入排名公式，仅用于报告展示
     */
    EVIDENCE_NARRATIVE("匹配证据链叙事", new String[]{"POST_ABILITY_MODEL", "ABILITY_TAG", "CONTEST_EVIDENCE"}, 3, 0.6, false, false),

    /**
     * 证据追溯
     * 检索：证据相关的知识文档
     */
    EVIDENCE_TRACE("证据追溯", new String[]{"CONTEST_EVIDENCE", "KNOWLEDGE_DOC"}, 3, 0.7, false, true),

    /**
     * 报告生成
     * 检索：证据中心（仅 VERIFIED 状态）、能力标签定义、岗位能力模型、知识文档
     * 用途：为 AI 报告生成提供可信上下文，不替代数据库统计
     */
    REPORT_GENERATION("报告生成", new String[]{"CONTEST_EVIDENCE", "ABILITY_TAG", "POST_ABILITY_MODEL", "KNOWLEDGE_DOC"}, 8, 0.6, false, true),

    /**
     * 知识文档问答
     * 检索：知识文档、外部趋势
     * 用途：基于知识库的语义问答
     */
    KNOWLEDGE_QA("知识文档问答", new String[]{"KNOWLEDGE_DOC", "EXTERNAL_TREND", "VOLCENGINE_KB"}, 5, 0.6, true, true),

    /**
     * 面试追问上下文
     * 检索：竞赛证据、知识文档
     * 用途：为面试追问提供上下文信息
     */
    INTERVIEW_FOLLOWUP("面试追问上下文", new String[]{"CONTEST_EVIDENCE", "KNOWLEDGE_DOC"}, 5, 0.6, false, true),

    /**
     * 匹配分析上下文
     * 检索：岗位能力模型、能力标签、竞赛证据、知识文档
     * 用途：为匹配分析Agent提供事实上下文
     */
    MATCHING_ANALYSIS("匹配分析上下文", new String[]{"POST_ABILITY_MODEL", "ABILITY_TAG", "CONTEST_EVIDENCE", "KNOWLEDGE_DOC"}, 5, 0.6, false, true),

    /**
     * 学习推荐上下文
     * 检索：学习资源、能力标签、知识文档
     * 用途：为学习推荐Agent提供事实上下文
     */
    LEARNING_RECOMMENDATION("学习推荐上下文", new String[]{"LEARNING_RESOURCE", "ABILITY_TAG", "KNOWLEDGE_DOC"}, 5, 0.6, false, true),

    // ========== 以下枚举已废弃，仅保留以保证编译通过，后续逐步移除 ==========

    /**
     * @deprecated 该场景无需语义检索，请直接使用 MyBatis-Plus Mapper 查询对应的结构化表
     */
    @Deprecated
    JD_ABILITY_EXTRACT("JD能力抽取", new String[]{"ABILITY_TAG", "POST_PROTOTYPE", "POST_ABILITY_MODEL", "JD_IMPORT", "VOLCENGINE_KB"}, 5, 0.6, true, true),

    /**
     * @deprecated 该场景无需语义检索，请直接使用 MyBatis-Plus Mapper 查询对应的结构化表
     */
    @Deprecated
    RESUME_ABILITY_EXTRACT("简历能力抽取", new String[]{"ABILITY_TAG", "EMP_ABILITY", "CONTEST_EVIDENCE", "VOLCENGINE_KB"}, 5, 0.6, true, true),

    /**
     * @deprecated 该场景无需语义检索，请直接使用 MyBatis-Plus Mapper 查询对应的结构化表
     */
    @Deprecated
    ABILITY_HALLUCINATION("能力幻觉防控", new String[]{"ABILITY_TAG", "CONTEST_EVIDENCE"}, 3, 0.7, false, true),

    /**
     * @deprecated 该场景无需语义检索，请直接使用 MyBatis-Plus Mapper 查询对应的结构化表
     */
    @Deprecated
    LEARNING_PATH("学习路径", new String[]{"LEARNING_RESOURCE", "VOLCENGINE_KB"}, 5, 0.5, true, true),

    /**
     * @deprecated 该场景无需语义检索，请直接使用 MyBatis-Plus Mapper 查询对应的结构化表
     */
    @Deprecated
    AI_LEARNING_SUGGESTION("AI学习建议", new String[]{"LEARNING_RESOURCE", "ABILITY_TAG", "CONTEST_EVIDENCE", "VOLCENGINE_KB"}, 8, 0.5, true, true),

    /**
     * @deprecated 该场景无需语义检索，请直接使用 MyBatis-Plus Mapper 查询对应的结构化表
     */
    @Deprecated
    COMPANY_POST_WEIGHT_GENERATION("公司岗位能力权重批量推荐", new String[]{"ABILITY_TAG", "POST_ABILITY_MODEL", "JD_IMPORT", "CONTEST_EVIDENCE", "KNOWLEDGE_DOC"}, 5, 0.6, false, true),

    /**
     * @deprecated 该场景无需语义检索，请直接使用 MyBatis-Plus Mapper 查询对应的结构化表
     */
    @Deprecated
    MATCH_GAP_DIAGNOSIS("综合差距诊断", new String[]{"POST_ABILITY_MODEL", "ABILITY_TAG", "CONTEST_EVIDENCE", "LEARNING_RESOURCE", "KNOWLEDGE_DOC"}, 8, 0.5, false, true);

    /** 场景名称 */
    private final String name;

    /** 允许检索的来源类型 */
    private final String[] allowedSourceTypes;

    /** 默认返回数量 */
    private final int defaultTopK;

    /** 最低相似度阈值 */
    private final double minSimilarity;

    /** 是否允许云知识库 */
    private final boolean allowCloud;

    /** 是否记录日志 */
    private final boolean logEnabled;

    RagScenarioEnum(String name, String[] allowedSourceTypes, int defaultTopK, double minSimilarity, boolean allowCloud, boolean logEnabled) {
        this.name = name;
        this.allowedSourceTypes = allowedSourceTypes;
        this.defaultTopK = defaultTopK;
        this.minSimilarity = minSimilarity;
        this.allowCloud = allowCloud;
        this.logEnabled = logEnabled;
    }

    /**
     * 检查给定的 sourceType 是否在本场景允许范围内
     */
    public boolean isSourceTypeAllowed(String sourceType) {
        if (allowedSourceTypes == null) {
            return true;
        }
        for (String allowed : allowedSourceTypes) {
            if (allowed.equals(sourceType)) {
                return true;
            }
        }
        return false;
    }
}
