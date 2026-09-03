package com.example.matching.entity.contest;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报告-证据关联实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("contest_report_evidence_ref")
public class ContestReportEvidenceRef implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 报告任务ID */
    private Long reportTaskId;

    /** 证据ID */
    private Long evidenceId;

    /** 证据编码快照 */
    private String evidenceCode;

    /** 来源类型快照 */
    private String sourceType;

    /** 能力名称快照 */
    private String abilityName;

    /** 置信度快照 */
    private BigDecimal confidenceScore;

    /** 可信度快照 */
    private BigDecimal credibilityScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
