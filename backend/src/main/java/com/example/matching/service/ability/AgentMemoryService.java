package com.example.matching.service.ability;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.entity.ability.PersonAbilityGovernanceEvent;

import java.util.List;

/**
 * Agent 记忆服务接口
 * <p>
 * 管理人工治理产生的经验记忆，提供给四个来源 Agent 查询使用。
 *
 * @author system
 */
public interface AgentMemoryService extends IService<AgentMemory> {

    /**
     * 创建记忆
     *
     * @param memory 记忆对象
     * @return 记忆ID
     */
    Long createMemory(AgentMemory memory);

    /**
     * 按适用范围查询活跃记忆
     *
     * @param scope 适用范围：ALL, RESUME_PARSE, AI_TEST, VIDEO_INTERVIEW, PMS_ANALYSIS
     * @return 记忆列表（按优先级排序）
     */
    List<AgentMemory> getActiveMemories(String scope);

    /**
     * 按类型查询活跃记忆
     *
     * @param memoryType 记忆类型：TAG_NORMALIZE, TAG_REJECT, LEVEL_RULE, SOURCE_WEIGHT, BOUNDARY_DEFINE
     * @return 记忆列表
     */
    List<AgentMemory> getMemoriesByType(String memoryType);

    /**
     * 搜索相关记忆（按触发表达匹配）
     *
     * @param text 待匹配文本
     * @param scope 适用范围
     * @return 匹配的记忆列表
     */
    List<AgentMemory> searchMemories(String text, String scope);

    /**
     * 按文本与scope检索ACTIVE规则（含结构化rulePayloadJson）。
     * 用于AgentMemoryContextService治理闭环检索。
     *
     * @param text  规范化后的文本
     * @param scope 应用范围
     * @return ACTIVE规则列表（按priority desc, updatedTime desc排序）
     */
    List<AgentMemory> searchActiveRules(String text, String scope);

    /**
     * 记忆被使用时更新统计
     *
     * @param memoryId 记忆ID
     */
    void markUsed(Long memoryId);

    /**
     * 记录规则命中日志
     *
     * @param memoryId 记忆ID
     * @param agentName Agent名称
     * @param sourceType 来源类型
     * @param sourceRefId 来源引用ID
     * @param hitText 命中文本
     * @param hitContextJson 命中上下文JSON
     * @param outcome 执行结果
     */
    void logHit(Long memoryId, String agentName, String sourceType, Long sourceRefId,
                String hitText, String hitContextJson, String outcome);

    /**
     * 标记使用并记录命中日志（同一事务）
     */
    void markUsedAndLogHit(Long memoryId, String agentName, String sourceType, Long sourceRefId,
                           String hitText, String hitContextJson, String outcome);

    /**
     * 将同ruleKey的旧规则（排除excludeId）设为SUPERSEDED
     *
     * @param ruleKey 规则唯一键
     * @param excludeId 排除的规则ID
     */
    void supersedeByRuleKey(String ruleKey, Long excludeId);

    /**
     * 批量创建记忆（治理事件批量生成）
     *
     * @param memories 记忆列表
     */
    void createMemories(List<AgentMemory> memories);

    /**
     * 获取所有标签归一记忆（用于标签规范化）
     *
     * @return 标签归一记忆列表
     */
    List<AgentMemory> getTagNormalizeMemories();

    /**
     * 获取所有标签拒绝记忆（用于过滤低质量标签）
     *
     * @return 标签拒绝记忆列表
     */
    List<AgentMemory> getTagRejectMemories();

    // ========== 治理中心方法 ==========

    /**
     * 分页查询Agent记忆
     *
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @param status     状态筛选
     * @param memoryType 记忆类型筛选
     * @param scope      适用范围筛选
     * @param keyword    关键词搜索
     * @return 分页结果
     */
    Page<AgentMemory> pageMemories(Integer pageNum, Integer pageSize, String status,
                                   String memoryType, String scope, String keyword);

    /**
     * 启用记忆
     *
     * @param memoryId 记忆ID
     */
    void enableMemory(Long memoryId);

    /**
     * 禁用记忆
     *
     * @param memoryId 记忆ID
     */
    void disableMemory(Long memoryId);

    /**
     * 过期记忆
     *
     * @param memoryId 记忆ID
     */
    void expireMemory(Long memoryId);

    /**
     * 将到期但仍处于 ACTIVE 状态的记忆批量迁移为 EXPIRED。
     *
     * @return 本次迁移的记录数
     */
    int expireDueMemories();

    /**
     * 分页查询治理事件
     *
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @param modifyType 修改类型筛选
     * @param empId      员工ID筛选
     * @param tagId      标签ID筛选
     * @return 分页结果
     */
    Page<PersonAbilityGovernanceEvent> pageEvents(Integer pageNum, Integer pageSize,
                                                   String modifyType, Long empId, Long tagId);

    /**
     * 获取治理事件详情
     *
     * @param eventId 事件ID
     * @return 治理事件
     */
    PersonAbilityGovernanceEvent getEventById(Long eventId);
}
