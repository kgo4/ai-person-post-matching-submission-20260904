package com.example.matching.common.constant;

/**
 * SourceRef 统一格式常量
 * <p>
 * 所有 Agent、Context、Harness 必须使用此常量定义的格式。
 * 废弃格式：EMP_ABILITY:1, POST_ABILITY_MODEL:1, ai:xxx, generated:xxx, rag:ABILITY_TAG:xxx
 *
 * @author system
 */
public final class SourceRefConstants {

    private SourceRefConstants() {
    }

    // ==================== 引用类型前缀 ====================

    /** 事实引用 - 来自业务表的真实数据 */
    public static final String PREFIX_FACT = "fact:";

    /** 证据引用 - 来自证据中心 */
    public static final String PREFIX_EVIDENCE = "evidence:";

    /** 匹配引用 - 来自匹配记录 */
    public static final String PREFIX_MATCHING = "matching:";

    /** 来源引用 - 来自数据源 */
    public static final String PREFIX_SOURCE = "source:";

    /** 反馈引用 - 来自反馈数据 */
    public static final String PREFIX_FEEDBACK = "feedback:";

    /** 知识图谱引用 */
    public static final String PREFIX_KG = "kg:";

    /** RAG 引用 */
    public static final String PREFIX_RAG = "rag:";

    /** 学习资源引用 */
    public static final String PREFIX_LEARNING = "learning:";

    // ==================== 实体类型 ====================

    /** 员工能力 */
    public static final String ENTITY_EMP_ABILITY = "EMP_ABILITY";

    /** 岗位能力模型 */
    public static final String ENTITY_POST_ABILITY_MODEL = "POST_ABILITY_MODEL";

    /** 竞赛证据 */
    public static final String ENTITY_CONTEST_EVIDENCE = "CONTEST_EVIDENCE";

    /** 匹配记录 */
    public static final String ENTITY_MATCHING_RECORD = "MATCHING_RECORD";

    /** 匹配反馈 */
    public static final String ENTITY_MATCHING_FEEDBACK = "MATCHING_FEEDBACK";

    /** 知识图谱节点 */
    public static final String ENTITY_NODE = "NODE";

    /** RAG 分块 */
    public static final String ENTITY_CHUNK = "CHUNK";

    /** 学习资源 */
    public static final String ENTITY_RESOURCE = "RESOURCE";

    /** 学习路径 */
    public static final String ENTITY_PATH = "PATH";

    /** 面试会话 */
    public static final String ENTITY_INTERVIEW_SESSION = "INTERVIEW_SESSION";

    /** 面试问题 */
    public static final String ENTITY_INTERVIEW_QUESTION = "INTERVIEW_QUESTION";

    /** 面试能力观察 */
    public static final String ENTITY_INTERVIEW_OBSERVATION = "INTERVIEW_OBSERVATION";

    /** 面试追问记录 */
    public static final String ENTITY_INTERVIEW_FOLLOW_UP = "INTERVIEW_FOLLOW_UP";

    /** 人员能力主张 */
    public static final String ENTITY_PERSON_ABILITY_CLAIM = "PERSON_ABILITY_CLAIM";

    /** 人员能力画像 */
    public static final String ENTITY_PERSON_ABILITY_PROFILE = "PERSON_ABILITY_PROFILE";

    // ==================== 数据源类型 ====================

    /** 简历解析 */
    public static final String SOURCE_RESUME_PARSE = "RESUME_PARSE";

    /** AI 测试 */
    public static final String SOURCE_AI_TEST = "AI_TEST";

    /** 视频面试 */
    public static final String SOURCE_VIDEO_INTERVIEW = "VIDEO_INTERVIEW";

    /** AI面试（新架构，输出InterviewAbilityObservation） */
    public static final String SOURCE_AI_INTERVIEW = "AI_INTERVIEW";

    /** PMS 分析 */
    public static final String SOURCE_PMS_ANALYSIS = "PMS_ANALYSIS";

    /** 项目系统 */
    public static final String SOURCE_PROJECT_SYSTEM = "PROJECT_SYSTEM";

    /** 学习成果 */
    public static final String SOURCE_LEARNING_OUTCOME = "LEARNING_OUTCOME";

    /** 手动导入 */
    public static final String SOURCE_MANUAL_IMPORT = "MANUAL_IMPORT";

    /** JD 导入 */
    public static final String SOURCE_JD_IMPORT = "JD_IMPORT";

    /** 岗位描述 */
    public static final String SOURCE_POST_DESCRIPTION = "POST_DESCRIPTION";

    /** 岗位模板 */
    public static final String SOURCE_POST_TEMPLATE = "POST_TEMPLATE";

    /** 市场 JD */
    public static final String SOURCE_MARKET_JD = "MARKET_JD";

    /** 岗位演化 */
    public static final String SOURCE_POST_EVOLUTION = "POST_EVOLUTION";

    /** 公司岗位权重 */
    public static final String SOURCE_COMPANY_POST_WEIGHT = "COMPANY_POST_WEIGHT";

    /** 手动岗位模型 */
    public static final String SOURCE_MANUAL_POST_MODEL = "MANUAL_POST_MODEL";

    /** 行业白皮书 */
    public static final String SOURCE_INDUSTRY_WHITEPAPER = "INDUSTRY_WHITEPAPER";

    /** 政策文件 */
    public static final String SOURCE_POLICY_DOCUMENT = "POLICY_DOCUMENT";

    /** 职业标准 */
    public static final String SOURCE_OCCUPATION_STANDARD = "OCCUPATION_STANDARD";

    /** 企业内部云知识库 */
    public static final String SOURCE_CLOUD_KNOWLEDGE_INTERNAL = "CLOUD_KNOWLEDGE_INTERNAL";

    /** 内部业务需求 */
    public static final String SOURCE_INTERNAL_BUSINESS_REQUIREMENT = "INTERNAL_BUSINESS_REQUIREMENT";

    /** 内部岗位需求 */
    public static final String SOURCE_INTERNAL_POST_REQUIREMENT = "INTERNAL_POST_REQUIREMENT";

    /** 市场报告 */
    public static final String SOURCE_MARKET_REPORT = "MARKET_REPORT";

    /** 招聘JD */
    public static final String SOURCE_RECRUITMENT_JD = "RECRUITMENT_JD";

    /** 知乎趋势外部资料（由服务端采集后生成） */
    public static final String SOURCE_ZHIHU_TREND = "ZHIHU_TREND";

    // ==================== 演化场景类型 ====================

    /** 岗位动态演化 */
    public static final String SCENARIO_POST_DYNAMIC_EVOLUTION = "POST_DYNAMIC_EVOLUTION";

    /** 新兴岗位发现 */
    public static final String SCENARIO_EMERGING_POST_DISCOVERY = "EMERGING_POST_DISCOVERY";

    /** 岗位能力变更 */
    public static final String SCENARIO_POST_ABILITY_CHANGE = "POST_ABILITY_CHANGE";

    /** 云知识库演化 */
    public static final String SCENARIO_CLOUD_KNOWLEDGE_EVOLUTION = "CLOUD_KNOWLEDGE_EVOLUTION";

    /** 行业趋势分析 */
    public static final String SCENARIO_INDUSTRY_TREND_ANALYSIS = "INDUSTRY_TREND_ANALYSIS";

    /** 人员能力提取 */
    public static final String SCENARIO_PERSON_ABILITY_EXTRACTION = "PERSON_ABILITY_EXTRACTION";

    /** AI面试能力观察 */
    public static final String SCENARIO_AI_INTERVIEW_OBSERVATION = "AI_INTERVIEW_OBSERVATION";

    // ==================== 声明类型扩展 ====================

    /** 新兴岗位 */
    public static final String CLAIM_TYPE_EMERGING_POST = "EMERGING_POST";

    /** 岗位能力变更 */
    public static final String CLAIM_TYPE_POST_ABILITY_CHANGE = "POST_ABILITY_CHANGE";

    /** 岗位任务变更 */
    public static final String CLAIM_TYPE_POST_TASK_CHANGE = "POST_TASK_CHANGE";

    /** 岗位工具变更 */
    public static final String CLAIM_TYPE_POST_TOOL_CHANGE = "POST_TOOL_CHANGE";

    /** 岗位硬性条件变更 */
    public static final String CLAIM_TYPE_POST_HARD_CONDITION_CHANGE = "POST_HARD_CONDITION_CHANGE";

    /** AI面试能力观察 */
    public static final String CLAIM_TYPE_INTERVIEW_ABILITY_OBSERVATION = "INTERVIEW_ABILITY_OBSERVATION";

    // ==================== 废弃格式（仅供识别，不要使用）====================

    /** @deprecated 使用 fact:EMP_ABILITY:{id} 替代 */
    @Deprecated
    public static final String DEPRECATED_EMP_ABILITY = "EMP_ABILITY:";

    /** @deprecated 使用 fact:POST_ABILITY_MODEL:{id} 替代 */
    @Deprecated
    public static final String DEPRECATED_POST_ABILITY_MODEL = "POST_ABILITY_MODEL:";

    /** @deprecated 不可信的 AI 自证来源 */
    @Deprecated
    public static final String DEPRECATED_AI_PREFIX = "ai:";

    /** @deprecated 不可信的生成来源 */
    @Deprecated
    public static final String DEPRECATED_GENERATED_PREFIX = "generated:";

    /** @deprecated 使用 rag:CHUNK:{id} 替代 */
    @Deprecated
    public static final String DEPRECATED_RAG_ABILITY_TAG = "rag:ABILITY_TAG:";

    // ==================== 工具方法 ====================

    /**
     * 构建事实引用
     *
     * @param entityType 实体类型
     * @param id         实体ID
     * @return 标准引用格式
     */
    public static String factRef(String entityType, Long id) {
        return PREFIX_FACT + entityType + ":" + id;
    }

    /**
     * 构建证据引用
     *
     * @param entityType 实体类型
     * @param id         实体ID
     * @return 标准引用格式
     */
    public static String evidenceRef(String entityType, Long id) {
        return PREFIX_EVIDENCE + entityType + ":" + id;
    }

    /**
     * 构建匹配引用
     *
     * @param entityType 实体类型
     * @param id         实体ID
     * @return 标准引用格式
     */
    public static String matchingRef(String entityType, Long id) {
        return PREFIX_MATCHING + entityType + ":" + id;
    }

    /**
     * 构建来源引用
     *
     * @param sourceType 来源类型
     * @param id         来源ID
     * @return 标准引用格式
     */
    public static String sourceRef(String sourceType, Long id) {
        return PREFIX_SOURCE + sourceType + ":" + id;
    }

    /**
     * 构建员工能力事实引用
     *
     * @param abilityId 能力ID
     * @return 标准引用格式
     */
    public static String empAbilityFactRef(Long abilityId) {
        return factRef(ENTITY_EMP_ABILITY, abilityId);
    }

    /**
     * 构建岗位能力模型事实引用
     *
     * @param modelId 模型ID
     * @return 标准引用格式
     */
    public static String postAbilityModelFactRef(Long modelId) {
        return factRef(ENTITY_POST_ABILITY_MODEL, modelId);
    }

    /**
     * 构建证据引用
     *
     * @param evidenceId 证据ID
     * @return 标准引用格式
     */
    public static String contestEvidenceRef(Long evidenceId) {
        return evidenceRef(ENTITY_CONTEST_EVIDENCE, evidenceId);
    }

    /**
     * 构建匹配记录引用
     *
     * @param recordId 记录ID
     * @return 标准引用格式
     */
    public static String matchingRecordRef(Long recordId) {
        return matchingRef(ENTITY_MATCHING_RECORD, recordId);
    }

    /**
     * 构建知识来源文档引用
     *
     * @param sourceType 来源类型
     * @param documentId 文档ID
     * @param chunkCode  切片编码
     * @return 标准引用格式：source:{sourceType}:{documentId}:{chunkCode}
     */
    public static String knowledgeSourceRef(String sourceType, Long documentId, String chunkCode) {
        return PREFIX_SOURCE + sourceType + ":" + documentId + ":" + chunkCode;
    }

    /**
     * 构建行业白皮书引用
     *
     * @param documentId 文档ID
     * @param chunkCode  切片编码
     * @return 标准引用格式
     */
    public static String industryWhitepaperRef(Long documentId, String chunkCode) {
        return knowledgeSourceRef(SOURCE_INDUSTRY_WHITEPAPER, documentId, chunkCode);
    }

    /**
     * 构建云知识库引用
     *
     * @param documentId 文档ID
     * @param chunkCode  切片编码
     * @return 标准引用格式
     */
    public static String cloudKnowledgeRef(Long documentId, String chunkCode) {
        return knowledgeSourceRef(SOURCE_CLOUD_KNOWLEDGE_INTERNAL, documentId, chunkCode);
    }

    /**
     * 构建招聘JD引用
     *
     * @param documentId 文档ID
     * @param chunkCode  切片编码
     * @return 标准引用格式
     */
    public static String recruitmentJdRef(Long documentId, String chunkCode) {
        return knowledgeSourceRef(SOURCE_RECRUITMENT_JD, documentId, chunkCode);
    }

    /**
     * 验证引用格式是否有效
     *
     * @param ref 引用字符串
     * @return 是否有效
     */
    public static boolean isValidFormat(String ref) {
        if (ref == null || ref.isEmpty()) {
            return false;
        }
        String[] parts = ref.split(":");
        return parts.length >= 3;
    }

    /**
     * 验证引用格式是否为标准格式（非废弃格式）
     *
     * @param ref 引用字符串
     * @return 是否为标准格式
     */
    public static boolean isStandardFormat(String ref) {
        if (!isValidFormat(ref)) {
            return false;
        }
        // 检查是否为废弃格式
        return !isDeprecatedFormat(ref);
    }

    /**
     * 验证引用格式是否为废弃格式
     *
     * @param ref 引用字符串
     * @return 是否为废弃格式
     */
    public static boolean isDeprecatedFormat(String ref) {
        if (ref == null) {
            return false;
        }
        return ref.startsWith(DEPRECATED_AI_PREFIX)
                || ref.startsWith(DEPRECATED_GENERATED_PREFIX)
                || ref.startsWith(DEPRECATED_RAG_ABILITY_TAG)
                || (ref.startsWith(DEPRECATED_EMP_ABILITY) && !ref.startsWith(PREFIX_FACT))
                || (ref.startsWith(DEPRECATED_POST_ABILITY_MODEL) && !ref.startsWith(PREFIX_FACT));
    }

    /**
     * 解析引用类型
     *
     * @param ref 引用字符串
     * @return 引用类型前缀，如 "fact:", "evidence:" 等
     */
    public static String parseRefType(String ref) {
        if (ref == null || !ref.contains(":")) {
            return null;
        }
        int firstColon = ref.indexOf(':');
        return ref.substring(0, firstColon + 1);
    }

    /**
     * 解析实体类型
     *
     * @param ref 引用字符串
     * @return 实体类型，如 "EMP_ABILITY", "POST_ABILITY_MODEL" 等
     */
    public static String parseEntityType(String ref) {
        if (ref == null || !ref.contains(":")) {
            return null;
        }
        String[] parts = ref.split(":");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }

    /**
     * 解析实体ID
     *
     * @param ref 引用字符串
     * @return 实体ID
     */
    public static Long parseEntityId(String ref) {
        if (ref == null || !ref.contains(":")) {
            return null;
        }
        String[] parts = ref.split(":");
        if (parts.length >= 3) {
            try {
                return Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
