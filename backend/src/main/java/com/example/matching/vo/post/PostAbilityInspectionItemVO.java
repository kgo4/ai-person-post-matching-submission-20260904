package com.example.matching.vo.post;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位能力巡检 - 单项能力明细
 * <p>
 * 展示岗位能力表（post_ability_model）中的一条能力，附带入库后聚合出的风险标注：
 * 来自治理准入记录（governance_admission）、提取台账（post_ability_grounding_record）以及能力自身字段信号。
 */
@Data
public class PostAbilityInspectionItemVO {

    /** post_ability_model 主键 */
    private Long id;

    private Long postId;

    /** 能力名称 */
    private String abilityName;

    /** 关联标签ID（可空，能力名称是独立权威字段） */
    private Long tagId;

    private String techStack;

    /** 最低要求等级 1-5 */
    private Integer minRequiredLevel;

    /** 权重 0-100 */
    private BigDecimal weight;

    /** 是否必填 0/1 */
    private Integer isRequired;

    /** 是否核心 0/1 */
    private Integer isCore;

    /** AI 管道来源标记（JD_IMPORT/POST_EVOLUTION 等），非 AI 写入为 NULL/MANUAL */
    private String sourceType;

    private String modelVersion;

    private String remark;

    private LocalDateTime createdTime;

    /** 聚合风险等级：NORMAL / WARN / HIGH */
    private String riskLevel;

    /** 中文风险标签列表 */
    private List<String> riskTags;

    /** 是否 AI 生成来源 */
    private Boolean aiSource;

    /** 治理准入判定（governance_admission.final_decision：PASS/REVIEW/BLOCK） */
    private String harnessDecision;

    /** 治理准入风险等级 */
    private String harnessRiskLevel;

    /** 治理准入判定原因（reasonJson） */
    private String harnessReason;

    /** 治理准入检查编码 */
    private String harnessCheckCode;

    /** 提取台账状态（post_ability_grounding_record.validation_status：SUBMITTED/DEFERRED/REJECTED） */
    private String groundingStatus;

    /** 提取台账原因 */
    private String groundingReason;

    /** 原始证据文本 */
    private String evidenceText;
}
