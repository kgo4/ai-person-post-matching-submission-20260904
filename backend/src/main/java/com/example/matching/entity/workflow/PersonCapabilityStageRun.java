package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人员能力评估阶段运行实体
 * <p>
 * 记录每个阶段的执行历史，支持幂等重试和状态追踪。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("person_capability_stage_run")
public class PersonCapabilityStageRun implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联的工作流ID */
    private Long workflowId;

    /** 阶段类型（StageTypeEnum code） */
    private String stageType;

    /** 阶段状态：PENDING/RUNNING/SUCCEEDED/FAILED_RETRYABLE/FAILED_FINAL/SKIPPED/CANCELLED */
    private String status;

    /** 输入哈希（幂等键） */
    private String inputHash;

    /** 输入快照JSON */
    private String inputSnapshotJson;

    /** 输出快照JSON */
    private String outputSnapshotJson;

    /** 来源引用类型 */
    private String sourceRefType;

    /** 来源引用ID */
    private Long sourceRefId;

    /** 异步任务ID */
    private String taskId;

    /** 尝试次数 */
    private Integer attemptCount;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 失败代码 */
    private String failureCode;

    /** 失败信息 */
    private String failureMessage;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
