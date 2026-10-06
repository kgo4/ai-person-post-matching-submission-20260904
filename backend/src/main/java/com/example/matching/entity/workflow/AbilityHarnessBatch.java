package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聚合 Harness 审核批次实体
 * <p>
 * 面试完成后按能力聚合证据，执行一次批量 Harness 审核。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ability_harness_batch")
public class AbilityHarnessBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联的工作流ID */
    private Long workflowId;

    /** 批次类型 */
    private String batchType;

    /** 模型配置快照JSON */
    private String modelConfigSnapshot;

    /** 请求哈希 */
    private String requestHash;

    /** 请求快照JSON */
    private String requestSnapshotJson;

    /** 响应快照JSON */
    private String responseSnapshotJson;

    /** 批次状态 */
    private String status;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

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
