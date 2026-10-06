package com.example.matching.config;

/**
 * Redis 缓存名称常量
 * <p>
 * 命名规范：{领域}:{实体}:{维度}
 * <p>
 * 用法：
 * <pre>
 * // Service 层
 * {@literal @}Cacheable(cacheNames = RedisCacheNames.ABILITY_TAG_TREE)
 * public AbilityTagTreeVO getTree() { ... }
 * </pre>
 * <p>
 * 对应 Redis key 格式：matching:{cacheName}::{key}
 * 例如：matching:system:ability-tag:tree::all
 */
public final class RedisCacheNames {

    private RedisCacheNames() {
    }

    // ==================== 字典数据（长 TTL） ====================

    /** 能力标签树（树形结构） */
    public static final String ABILITY_TAG_TREE = "system:ability-tag:tree";

    /** 能力标签按分类列表（平铺，按分类） */
    public static final String ABILITY_TAG_CATEGORY_LIST = "system:ability-tag:category-list";

    /** 演化引擎使用的启用标签列表 */
    public static final String EVOLUTION_ACTIVE_ABILITY_TAGS = "evolution:ability-tag:active";

    /** 能力标签详情（按 tagId，TTL 30min） */
    public static final String ABILITY_TAG_INFO = "system:ability-tag:info";

    // ==================== 岗位数据（中 TTL） ====================

    /** 岗位能力模型（按岗位ID） */
    public static final String POST_MODEL = "post:model";

    /** 启用岗位列表 */
    public static final String POST_ENABLED = "post:post:enabled";

    // ==================== Dashboard（短 TTL） ====================

    /** Dashboard 统计聚合数据 */
    public static final String DASHBOARD_STATS = "dashboard:stats";

    // ==================== 匹配数据（中 TTL） ====================

    /** 匹配记录分页查询（按组合筛选 key） */
    public static final String MATCHING_RECORD_PAGE = "matching:record:page";

    /** 匹配记录详情（按记录ID） */
    public static final String MATCHING_RECORD_DETAIL = "matching:record:detail";

    /** AI 语义分析报告（按记录ID） */
    public static final String MATCHING_AI_REPORT = "matching:report:ai";

    // ==================== 标签归一（长 TTL） ====================

    /** 标签标准ID映射（按标签ID） */
    public static final String TAG_CANONICAL = "system:tag:canonical";

    // ==================== 认证数据（中 TTL） ====================

    /** 用户认证信息（按 username，TTL 30min，变更时主动清除） */
    public static final String AUTH_SYSUSER = "auth:sysuser";

    /** User authorities resolved from user-role and role mappings. */
    public static final String AUTH_AUTHORITIES = "auth:authorities";

    // ==================== 向量召回（中 TTL） ====================

    /** 向量召回结果（按岗位ID） */
    public static final String VECTOR_RECALL = "vector:recall";

    // ==================== 导入数据（短 TTL） ====================

    /** Excel导入预览结果（按批次ID） */
    public static final String IMPORT_PREVIEW = "post:import:preview";

    /** Excel导入分析进度（按批次ID） */
    public static final String IMPORT_PROGRESS = "post:import:progress";

    // ==================== 向量缓存（长 TTL） ====================

    /** 标签嵌入向量（按标签ID，TTL 24h） */
    public static final String TAG_VECTOR = "vector:tag";

    /** 员工画像查询向量（按员工ID，TTL 1h） */
    public static final String EMP_VECTOR = "vector:emp";

    public static final String EMP_VECTOR_CACHE_EPOCH = "vector:emp:epoch";

    // ==================== 分页查询缓存（中 TTL） ====================

    /** 员工分页查询（按 page+keyword+status 组合 key） */
    public static final String EMP_EMPLOYEE_PAGE = "emp:employee:page";

    /** 岗位分页查询（按 page+keyword+status 组合 key） */
    public static final String POST_POST_PAGE = "post:post:page";
}
