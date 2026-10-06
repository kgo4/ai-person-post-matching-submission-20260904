package com.example.matching.entity.ability;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 记忆实体
 * <p>
 * 记录人工治理产生的经验记忆，用于反哺四个来源 Agent。
 * 记忆类型包括：标签归一、标签拒绝、等级判断、来源权重、边界定义。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "agent_memory", autoResultMap = true)
public class AgentMemory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 记忆类型：TAG_NORMALIZE, TAG_REJECT, LEVEL_RULE, TERM_INTERPRETATION, SOURCE_POLICY */
    private String memoryType;

    /** 记忆标题（简要描述） */
    private String title;

    /** 记忆内容（面向管理员的说明文本） */
    private String content;

    /** 触发表达JSON（触发这条记忆的关键词/模式） */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String triggerExpressionsJson;

    /** 结构化规则载荷JSON（程序执行依据） */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String rulePayloadJson;

    /** 规则强度：HARD-程序强制执行，GUIDANCE-Prompt建议 */
    private String ruleStrength;

    /** 规则唯一键：由scope+memoryType+condition+action计算SHA-256，用于幂等去重 */
    private String ruleKey;

    /** 适用范围：EMPLOYEE_ABILITY_EXTRACTION, POST_ABILITY_EXTRACTION, EVIDENCE_GOVERNANCE, ALL */
    private String applicableScope;

    /** 优先级：数字越大优先级越高 */
    private Integer priority;

    /** 状态：DRAFT, ACTIVE, DISABLED, SUPERSEDED, EXPIRED */
    private String status;

    /** 来源治理事件ID */
    private Long sourceEventId;

    /** 使用次数 */
    private Integer useCount;

    /** 最后使用时间 */
    private LocalDateTime lastUsedTime;

    /** 失效时间（可选） */
    private LocalDateTime expireTime;

    /** 规则修订号（初始 1） */
    private Integer revision;

    /** 被本修订取代的旧记忆 ID（无则 NULL） */
    private Long supersedesMemoryId;

    /** 用于语义检索的记忆嵌入向量 */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Float> embeddingVector;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer isDeleted;
}
