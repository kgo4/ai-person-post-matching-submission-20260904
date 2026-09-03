package com.example.matching.entity.employee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI测试记录表实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emp_ai_test")
public class EmpAiTest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long empId;

    /** 岗位综合能力测试对应的岗位 ID；普通能力标签测试为空 */
    private Long postId;

    /** 关联的能力评估工作流ID（工作流测试专用） */
    private Long workflowId;

    /** 测试标题 */
    private String testTitle;

    /** 测试的能力标签ID */
    private Long abilityTagId;

    /** 能力标签名称（冗余） */
    private String abilityTagName;

    /** 题目列表，JSON数组 */
    private String questions;

    /** 员工提交的答案，JSON对象 */
    private String answers;

    /** AI批阅结果，JSON格式 */
    private String aiEvaluation;

    /** 得分，0-100 */
    private BigDecimal score;

    /** AI评估的掌握等级：1入门/2熟悉/3掌握/4精通/5专家 */
    private Integer masteryLevel;

    /** AI生成的分析报告 */
    private String analysisReport;

    /** 生成或评估失败时的错误信息 */
    private String errorMessage;

    /** 状态：-1生成中/0待作答/1评估中/2已完成/3已导入 */
    private Integer status;

    /** 题目生成状态：PENDING/PROCESSING/SUCCEEDED/FAILED */
    private String generationState;

    /** 评分状态：PENDING/PROCESSING/SUCCEEDED/FAILED */
    private String evaluationState;

    /** 本次处理开始时间（僵尸恢复依据） */
    private LocalDateTime processingStartedAt;

    /** 重试次数 */
    private Integer retryCount;

    /** 最近一次错误类型，如 AI_OUTPUT_INVALID */
    private String lastErrorType;

    /** 最近一次错误信息 */
    private String lastErrorMessage;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 完成时间 */
    private LocalDateTime completedTime;

    /** 导入能力档案时间 */
    private LocalDateTime importedTime;
}
