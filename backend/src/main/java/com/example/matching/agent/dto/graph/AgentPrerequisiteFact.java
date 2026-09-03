package com.example.matching.agent.dto.graph;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 预计算前置关系（能力先修）。
 */
@Data
public class AgentPrerequisiteFact {

    /** 能力标签 ID */
    private Long abilityTagId;

    /** 前置能力标签 ID */
    private Long prerequisiteAbilityTagId;

    /** 能力名称 */
    private String abilityName;

    /** 前置能力名称 */
    private String prerequisiteAbilityName;

    /** 关系类型（如 PREREQUISITE_OF） */
    private String relationType;

    /** 来源引用 */
    private List<String> sourceRefs = new ArrayList<>();

    public static AgentPrerequisiteFact of(Long abilityTagId, Long prerequisiteAbilityTagId,
                                           String abilityName, String prerequisiteAbilityName,
                                           String relationType) {
        AgentPrerequisiteFact fact = new AgentPrerequisiteFact();
        fact.setAbilityTagId(abilityTagId);
        fact.setPrerequisiteAbilityTagId(prerequisiteAbilityTagId);
        fact.setAbilityName(abilityName);
        fact.setPrerequisiteAbilityName(prerequisiteAbilityName);
        fact.setRelationType(relationType);
        return fact;
    }
}
