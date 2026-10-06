package com.example.matching.entity.ability;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 记忆命中日志实体
 * <p>
 * 记录 Agent 使用记忆的详细情况，用于统计记忆的价值和效果。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "agent_memory_hit_log", autoResultMap = true)
public class AgentMemoryHitLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 记忆ID */
    private Long memoryId;

    /** Agent名称：EMPLOYEE_ABILITY_EXTRACTION, POST_ABILITY_EXTRACTION, EVIDENCE_GOVERNANCE */
    private String agentName;

    /** 来源类型：RESUME, AI_TEST, VIDEO_INTERVIEW, PMS, MANUAL, JD, POST_DESCRIPTION */
    private String sourceType;

    /** 来源引用ID（如简历ID、测试ID等） */
    private Long sourceRefId;

    /** 命中文本（触发记忆的文本片段） */
    private String hitText;

    /** 命中上下文JSON（scope、ruleStrength、retrievalRank、outcome、before/after） */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String hitContextJson;

    /** 执行结果：RETRIEVED_NOT_APPLIED, APPLIED_BY_AGENT, APPLIED_BY_CODE, CONFLICT_SUPERSEDED, REJECTED_BY_VALIDATION */
    private String outcome;

    /** 命中时间 */
    @TableField(value = "hit_time", fill = FieldFill.INSERT)
    private LocalDateTime hitTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer isDeleted;
}
