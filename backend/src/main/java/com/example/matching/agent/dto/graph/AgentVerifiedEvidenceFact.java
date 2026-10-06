package com.example.matching.agent.dto.graph;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 已验证员工证据事实。
 * <p>
 * 只允许 reviewStatus = VERIFIED，且必须归属当前员工能力（targetType=EMP_ABILITY）。
 */
@Data
public class AgentVerifiedEvidenceFact {

    /** 证据 ID */
    private Long evidenceId;

    /** 员工能力 ID（targetRefId） */
    private Long empAbilityId;

    /** 能力标签 ID */
    private Long abilityTagId;

    /** 证据文本 */
    private String evidenceText;

    /** 审核状态（固定 VERIFIED） */
    private String reviewStatus;

    /** 来源引用（fact:EVIDENCE:xxx） */
    private List<String> sourceRefs = new ArrayList<>();

    public static AgentVerifiedEvidenceFact of(Long evidenceId, Long empAbilityId,
                                               Long abilityTagId, String evidenceText) {
        AgentVerifiedEvidenceFact fact = new AgentVerifiedEvidenceFact();
        fact.setEvidenceId(evidenceId);
        fact.setEmpAbilityId(empAbilityId);
        fact.setAbilityTagId(abilityTagId);
        fact.setEvidenceText(evidenceText);
        fact.setReviewStatus("VERIFIED");
        return fact;
    }
}
