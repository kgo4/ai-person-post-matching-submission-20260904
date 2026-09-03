package com.example.matching.entity.evolution;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位演化任务实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_evolution_task")
public class PostEvolutionTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 任务编码，稳定可读ID */
    private String taskCode;

    /** 岗位ID */
    private Long postId;

    /** 任务名称 */
    private String taskName;

    /** 基线模型版本 */
    private String baselineVersion;

    /** 新JD或市场数据文本 */
    private String newJdText;

    /** RAG查询日志ID */
    private Long ragQueryLogId;

    /** 任务状态：PENDING/RUNNING/WAIT_CONFIRM/APPLIED/FAILED */
    private String taskStatus;

    /** 任务摘要JSON */
    private String summaryJson;

    /** 错误信息 */
    private String errorMessage;

    /** 来源类型：WHITEPAPER_UPLOAD/CLOUD_KNOWLEDGE_SYNC/RECRUITMENT_DATA_IMPORT/MANUAL/SCHEDULED_SCAN */
    private String sourceType;

    /** 来源文档ID（关联 KnowledgeSourceDocument） */
    private Long sourceDocumentId;

    /** 业务领域 */
    private String businessDomain;

    /** 行业 */
    private String industry;

    /** 触发类型：WHITEPAPER_UPLOAD/CLOUD_KNOWLEDGE_SYNC/RECRUITMENT_DATA_IMPORT/MANUAL/SCHEDULED_SCAN */
    private String triggerType;

    /** 上下文哈希 */
    private String contextHash;

    /** 上下文快照ID */
    private Long contextSnapshotId;

    /** 证据摘要JSON */
    private String evidenceSummary;

    /** Agent执行过程追踪JSON */
    private String agentTrace;

    /** Harness校验摘要JSON */
    private String harnessSummary;

    /** 执行阶段：READING_MODEL/RETRIEVING_INDUSTRY/RETRIEVING_INTERNAL/GENERATING_SIGNALS/HARNESS_CHECK/GENERATING_ITEMS/COMPLETED */
    private String progressStatus;

    /** 执行进度百分比：0-100 */
    private Integer progressPercent;

    /** 关联的知识源文档ID列表JSON */
    private String sourceDocumentIds;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
