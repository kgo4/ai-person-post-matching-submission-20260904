package com.example.matching.dto.matching;

import com.example.matching.common.enums.MatchTypeEnum;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 匹配明细DTO
 * <p>
 * 每个岗位能力项的匹配命中详情，用于报告与调试。
 *
 * @author system
 */
@Data
public class MatchDetailDTO {

    /** 岗位要求的标签ID */
    private Long requiredTagId;

    /** 岗位要求标签的标准标签ID */
    private Long requiredCanonicalTagId;

    /** 命中的员工标签ID */
    private Long matchedEmpTagId;

    /** 命中的正式人员能力记录 ID；标签仅是可选增强信息。 */
    private Long matchedEmpAbilityId;

    /** 命中的正式人员能力名称。 */
    private String matchedEmpAbilityName;

    /** 命中员工标签的标准标签ID */
    private Long matchedEmpCanonicalTagId;

    /** 命中类型 */
    private MatchTypeEnum matchType;

    /** 相似度分数（语义兜底时为实时计算值） */
    private BigDecimal similarityScore;

    /** 命中系数 */
    private BigDecimal matchCoefficient;

    /** 员工原始能力等级（融合后） */
    private BigDecimal employeeRawLevel;

    /** 有效等级（employeeRawLevel * matchCoefficient） */
    private BigDecimal effectiveLevel;

    /** 岗位要求等级 */
    private Integer requiredLevel;

    /** 是否通过（必填项判断时使用） */
    private boolean passed;

    /** 得分贡献 */
    private BigDecimal scoreContribution;

    /** 是否必填能力 */
    private boolean required;

    /** 是否核心能力 */
    private boolean core;

    /**
     * 创建未命中的明细
     */
    public static MatchDetailDTO noMatch(Long requiredTagId, Long requiredCanonicalTagId,
                                          Integer requiredLevel, boolean required, boolean core) {
        MatchDetailDTO detail = new MatchDetailDTO();
        detail.setRequiredTagId(requiredTagId);
        detail.setRequiredCanonicalTagId(requiredCanonicalTagId);
        detail.setMatchType(MatchTypeEnum.NONE);
        detail.setSimilarityScore(BigDecimal.ZERO);
        detail.setMatchCoefficient(BigDecimal.ZERO);
        detail.setEmployeeRawLevel(BigDecimal.ZERO);
        detail.setEffectiveLevel(BigDecimal.ZERO);
        detail.setRequiredLevel(requiredLevel);
        detail.setPassed(false);
        detail.setScoreContribution(BigDecimal.ZERO);
        detail.setRequired(required);
        detail.setCore(core);
        return detail;
    }

    /**
     * 获取命中类型的中文描述
     */
    public String getMatchTypeDescription() {
        return matchType != null ? matchType.getDescription() : "未知";
    }

    /**
     * 获取通过状态的中文描述
     */
    public String getPassedDescription() {
        return passed ? "通过" : "未通过";
    }
}
