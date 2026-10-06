package com.example.matching.entity.harness;

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
@TableName("ai_harness_check_log")
public class AiHarnessCheckLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String checkCode;
    private String scenario;
    private String claimType;
    private String claimText;
    private String sourceType;
    private Long sourceRefId;
    private String evidenceText;
    private String ragChunkIds;
    private String sourceRefs;
    private Long matchedTagId;
    private Long similarTagId;
    private BigDecimal supportScore;
    private String riskLevel;
    private String decision;
    private Integer isSelfEvidence;
    private String reasonJson;
    private String reviewStatus;
    private String reviewComment;
    private LocalDateTime reviewedTime;

    /** 业务应用状态：PENDING / APPLIED / SKIPPED */
    private String businessApplyStatus;
    /** 业务目标类型：EMP_ABILITY / POST_ABILITY 等 */
    private String businessTargetType;
    /** 业务目标 ID（写入后的业务数据 ID） */
    private Long businessTargetId;

    /** 上下文哈希 */
    private String contextHash;
    /** 上下文快照ID */
    private Long contextSnapshotId;
    /** 声明载荷JSON */
    private String claimPayloadJson;
    /** 接受的来源引用JSON */
    private String acceptedSourceRefs;
    /** 无效的来源引用JSON */
    private String invalidSourceRefs;
    /** 缺失的证据JSON */
    private String missingEvidenceJson;

    /** 原支持分数 */
    private BigDecimal legacySupportScore;
    /** 原决策 */
    private String legacyDecision;
    /** 决策合并规则 */
    private String decisionRule;
    /** 追踪ID */
    private String traceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
