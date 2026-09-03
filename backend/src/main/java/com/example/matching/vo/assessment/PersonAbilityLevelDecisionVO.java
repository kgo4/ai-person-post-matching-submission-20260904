package com.example.matching.vo.assessment;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 能力等级决策视图对象（Controller 层返回，避免直接暴露实体）
 *
 * @author system
 */
@Data
public class PersonAbilityLevelDecisionVO {

    private Long id;
    private Long workflowId;
    private Long claimGroupId;
    private Long empId;
    private Long tagId;
    /** 决策状态（DecisionStatusEnum code） */
    private String decisionStatus;
    /** 最终等级：1-5 */
    private Integer finalLevel;
    /** 最终置信度：0-100 */
    private Integer finalConfidence;
    /** 审核状态：AUTO/PENDING/APPROVED/REJECTED */
    private String reviewState;
    /** 策略版本号 */
    private String policyVersion;
    /** 决策原因码JSON */
    private String decisionReasonCodesJson;
    /** 审核人ID */
    private Long reviewedBy;
    private LocalDateTime reviewedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    /** 从实体构建 VO */
    public static PersonAbilityLevelDecisionVO from(
            com.example.matching.entity.workflow.PersonAbilityLevelDecision entity) {
        PersonAbilityLevelDecisionVO vo = new PersonAbilityLevelDecisionVO();
        vo.setId(entity.getId());
        vo.setWorkflowId(entity.getWorkflowId());
        vo.setClaimGroupId(entity.getClaimGroupId());
        vo.setEmpId(entity.getEmpId());
        vo.setTagId(entity.getTagId());
        vo.setDecisionStatus(entity.getDecisionStatus());
        vo.setFinalLevel(entity.getFinalLevel());
        vo.setFinalConfidence(entity.getFinalConfidence());
        vo.setReviewState(entity.getReviewState());
        vo.setPolicyVersion(entity.getPolicyVersion());
        vo.setDecisionReasonCodesJson(entity.getDecisionReasonCodesJson());
        vo.setReviewedBy(entity.getReviewedBy());
        vo.setReviewedTime(entity.getReviewedTime());
        vo.setCreatedTime(entity.getCreatedTime());
        vo.setUpdatedTime(entity.getUpdatedTime());
        return vo;
    }
}
