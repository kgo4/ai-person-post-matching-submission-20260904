package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人员能力评估工作流实体
 * <p>
 * 一个员工一次完整的候选人评估流程。同一时间只允许一个活跃流程。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("person_capability_workflow")
public class PersonCapabilityWorkflow implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long empId;

    /** 关联的简历解析记录ID */
    private Long resumeParseId;

    /** 绑定的目标岗位ID（测试选岗后锁定，面试复用，单一真相源） */
    private Long postId;

    /** 工作流状态（WorkflowStatusEnum code） */
    private String status;

    /** 当前阶段（StageTypeEnum code） */
    private String currentStage;

    /** 当前活跃的阶段运行ID */
    private Long activeStageRunId;

    /** 工作流版本号 */
    private Integer workflowVersion;

    /** 流程开始时间 */
    private LocalDateTime startedAt;

    /** 流程完成时间 */
    private LocalDateTime completedAt;

    /** 失败原因 */
    private String failedReason;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

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
