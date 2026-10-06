package com.example.matching.entity.governance;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("governance_admission")
public class GovernanceAdmissionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String admissionCode;
    private String scenario;
    private String claimType;
    private String claimText;
    private String sourceType;
    private Long sourceRefId;
    private String evidenceText;
    private String sourceRefsJson;
    private String ragChunkIdsJson;
    private Long matchedTagId;
    private Long similarTagId;

    private BigDecimal legacySupportScore;
    private BigDecimal harnessSupportScore;
    private BigDecimal finalSupportScore;
    private String legacyDecision;
    private String harnessDecision;
    private String finalDecision;
    private String decisionRule;
    private String harnessCheckCode;
    private String traceId;

    private String riskLevel;
    private Integer isSelfEvidence;
    private String reasonJson;
    private String acceptedSourceRefsJson;
    private String invalidSourceRefsJson;
    private String missingEvidenceJson;

    private String businessTargetType;
    private Long businessTargetId;
    private String applyStatus;

    private Long contextSnapshotId;
    private String contextHash;
    private String claimPayloadJson;

    /** RETRYABLE 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间（RETRYABLE 状态使用） */
    private LocalDateTime nextRetryTime;

    private String reviewStatus;
    private String reviewComment;
    private LocalDateTime reviewedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
