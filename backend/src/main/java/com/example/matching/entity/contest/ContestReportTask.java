package com.example.matching.entity.contest;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 竞赛报告任务实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("contest_report_task")
public class ContestReportTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 任务编码 */
    private String taskCode;

    /** 报告类型：SUMMARY/EVALUATION/GRAPH/EVIDENCE/SUBMISSION_CHECKLIST/MATCHING_OVERVIEW */
    private String reportType;

    /** 任务状态：PENDING/RUNNING/VALIDATING/SUCCEEDED/FAILED/PARTIAL_SUCCEEDED */
    private String taskStatus;

    /** 报告标题 */
    private String reportTitle;

    /** 生成的报告Markdown */
    private String reportMarkdown;

    /** 生成的报告JSON（结构化摘要、指标、引用证据、风险项、建议项） */
    private String reportJson;

    /** 错误信息 */
    private String errorMessage;

    /** 生成模式：STAT_ONLY/AI/AI_RAG */
    private String generationMode;

    /** 使用的模型名称 */
    private String modelName;

    /** Prompt 模板版本 */
    private String promptVersion;

    /** 证据快照JSON（报告生成时引用的证据列表） */
    private String evidenceSnapshotJson;

    /** 校验状态：PASSED/FAILED/PARTIAL/SKIPPED */
    private String validationStatus;

    /** 校验消息 */
    private String validationMessage;

    /** 生成耗时（毫秒） */
    private Long durationMs;

    /** 报告字数 */
    private Integer wordCount;

    /** RAG 场景 */
    private String ragScenario;

    /** RAG 命中数量 */
    private Integer ragHitCount;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 完成时间 */
    private LocalDateTime finishedTime;
}
