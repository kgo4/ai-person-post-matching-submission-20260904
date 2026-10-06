package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("assessment_evidence_ledger")
public class AssessmentEvidenceLedger {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long workflowId;
    private Long assessmentAbilityId;
    private Long canonicalTagId;
    private String sourceType;
    private Long sourceRefId;
    private Long questionId;
    private String evidenceText;
    private BigDecimal score;
    private Integer observedLevel;
    private BigDecimal confidenceScore;
    private String evidenceStatus;
    private String sourceRefsJson;
    private String scopeHash;
    private LocalDateTime createdTime;
}
