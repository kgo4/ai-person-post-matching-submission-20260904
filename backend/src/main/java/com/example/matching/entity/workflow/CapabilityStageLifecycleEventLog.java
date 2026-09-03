package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 能力评估生命周期事件日志实体
 * <p>
 * 协调器处理事件的幂等去重键（eventId 唯一）与状态链路审计记录。
 * 业务服务不直接写本表，仅由 CapabilityAssessmentLifecycleCoordinator 维护。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("capability_stage_lifecycle_event_log")
public class CapabilityStageLifecycleEventLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 事件唯一ID（幂等键） */
    private String eventId;

    /** 工作流ID */
    private Long workflowId;

    /** 阶段运行ID */
    private Long stageRunId;

    /** 阶段类型（StageTypeEnum code） */
    private String stageType;

    /** 生命周期事件类型（StageLifecycleEventType code） */
    private String eventType;

    /** 来源引用类型 */
    private String sourceRefType;

    /** 来源引用ID */
    private Long sourceRefId;

    /** 处理前工作流状态 */
    private String workflowStatusBefore;

    /** 处理后工作流状态 */
    private String workflowStatusAfter;

    /** 处理前阶段运行状态 */
    private String stageRunStatusBefore;

    /** 处理后阶段运行状态 */
    private String stageRunStatusAfter;

    /** 处理结果：HANDLED/SKIPPED_DUPLICATE/SKIPPED_ILLEGAL/STAGE_RUN_NOT_FOUND/FAILED */
    private String handledResult;

    /** 备注 */
    private String remark;

    /** 事件发生时间 */
    private LocalDateTime occurredAt;

    /** 记录时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
