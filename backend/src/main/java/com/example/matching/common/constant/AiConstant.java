package com.example.matching.common.constant;

/**
 * AI相关常量
 */
public final class AiConstant {

    private AiConstant() {
    }

    /** 匹配策略：加权余弦相似度 */
    public static final String STRATEGY_WEIGHTED_COSINE = "WEIGHTED_COSINE";

    /** 匹配策略：规则引擎 */
    public static final String STRATEGY_RULE_ENGINE = "RULE_ENGINE";

    /** 匹配策略：混合模式 */
    public static final String STRATEGY_HYBRID = "HYBRID";

    /** AI模型：GPT-4o */
    public static final String MODEL_GPT4O = "gpt-4o";

    /** Prompt模板：扩展字段解析 */
    public static final String PROMPT_TEMPLATE_EXTEND_FIELD = "extend-field-parse-prompt.ftl";

    /** 默认向量维度 */
    public static final int DEFAULT_VECTOR_DIMENSION = 1536;

    /** Milvus集合名称：员工能力向量 */
    public static final String COLLECTION_EMP_ABILITY = "emp_ability_vectors";

    /** Milvus集合名称：岗位要求向量 */
    public static final String COLLECTION_POST_REQUIREMENT = "post_requirement_vectors";

    /** AI评分状态：等待AI评分 */
    public static final String AI_SCORING_PENDING = "PENDING";

    /** AI评分状态：处理中 */
    public static final String AI_SCORING_PROCESSING = "PROCESSING";

    /** AI评分状态：已完成 */
    public static final String AI_SCORING_COMPLETED = "COMPLETED";

    /** AI评分状态：失败（可重试） */
    public static final String AI_SCORING_FAILED = "FAILED";

    /** AI评分状态：已跳过（不启用AI） */
    public static final String AI_SCORING_SKIPPED = "SKIPPED";

    /** AI评分最大重试次数 */
    public static final int AI_SCORING_MAX_RETRIES = 3;

    /** AI评分重试间隔（毫秒） */
    public static final long AI_SCORING_RETRY_DELAY_MS = 15_000;
    public static final long AI_SCORING_MAX_RETRY_DELAY_MS = 300_000;
}
