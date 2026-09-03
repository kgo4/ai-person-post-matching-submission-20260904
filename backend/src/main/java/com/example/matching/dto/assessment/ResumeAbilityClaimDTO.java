package com.example.matching.dto.assessment;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 简历提取能力主张 DTO
 * <p>
 * 阶段 1 提取 Agent 输出的每个 Claim 必须包含这些字段。
 * 仅保存为证据，不触发正式入库。
 *
 * @author system
 */
@Data
public class ResumeAbilityClaimDTO {

    /** 能力名称 */
    private String abilityName;

    /** 标准化能力名称 */
    private String normalizedAbilityName;

    /** 声明等级：1-5 */
    private Integer claimedLevel;

    /** 原文证据 */
    private String evidenceText;

    /** 来源引用列表 */
    private List<String> sourceRefs = new ArrayList<>();

    /** 来源类型：固定 RESUME_PARSE */
    private String sourceType = "RESUME_PARSE";

    /** 来源引用ID（简历解析记录ID） */
    private Long sourceRefId;

    /** 置信度：0-100 */
    private BigDecimal confidenceScore;

    /** 证据在解析后简历文本中的定位（用于确定性校验） */
    private String evidenceLocation;
}
