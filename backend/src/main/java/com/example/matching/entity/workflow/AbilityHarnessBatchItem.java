package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聚合 Harness 审核批次项实体
 * <p>
 * 每个能力聚合组的 Harness 审核结果。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ability_harness_batch_item")
public class AbilityHarnessBatchItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联的批次ID */
    private Long batchId;

    /** 关联的 Claim Group ID */
    private Long claimGroupId;

    /** 决策：PASS/REVIEW/BLOCK */
    private String decision;

    /** 能力是否得到支持 */
    private Integer abilitySupported;

    /** 支持的等级上限 */
    private Integer supportedLevelCeiling;

    /** 风险等级 */
    private String riskLevel;

    /** 原因码JSON数组 */
    private String reasonCodesJson;

    /** 证据引用JSON数组 */
    private String evidenceRefsJson;

    /** 关联的 Harness 日志ID */
    private Long harnessLogId;

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
