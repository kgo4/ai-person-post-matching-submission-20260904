package com.example.matching.dto.assessment;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 匹配预检结果 DTO
 * <p>
 * 强制匹配前检查人员能力资格。
 *
 * @author system
 */
@Data
public class EligibilityPrecheckResult {

    /** 员工ID */
    private Long empId;

    /** 是否有正式能力 */
    private Boolean hasConfirmedAbilities;

    /** 是否有待确立能力 */
    private Boolean hasProvisionalAbilities;

    /** 待确立能力数量 */
    private Integer provisionalAbilityCount;

    /** 相关待确立能力 */
    private List<ProvisionalAbilitySummary> relatedProvisionalAbilities = new ArrayList<>();

    /** 受影响的需求 */
    private List<String> affectedRequirements = new ArrayList<>();

    /** 风险标记 */
    private List<String> riskFlags = new ArrayList<>();

    /** 默认动作：NORMAL_MATCH / CONFIRMED_ONLY / FORBIDDEN / MANUAL_CONFIRM_REQUIRED */
    private String defaultAction;

    /**
     * 待确立能力摘要
     */
    @Data
    public static class ProvisionalAbilitySummary {
        private Long claimGroupId;
        private String abilityName;
        private Integer claimedLevel;
        private Integer evidenceCount;
        private String evidenceStatus;
        private String tagResolutionStatus;
        private String riskLabel;
    }
}
