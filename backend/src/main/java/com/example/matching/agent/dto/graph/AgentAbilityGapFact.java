package com.example.matching.agent.dto.graph;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 预计算能力差距。
 */
@Data
public class AgentAbilityGapFact {

    /** 能力标签 ID */
    private Long abilityTagId;

    /** 能力名称 */
    private String abilityName;

    /** 当前等级（权威源） */
    private Integer currentLevel;

    /** 目标等级（岗位要求） */
    private Integer targetLevel;

    /** 差距等级数 */
    private Integer gapLevel;

    /** 是否必填 */
    private boolean required;

    /** 是否核心 */
    private boolean core;

    /** 支撑该事实的来源引用 */
    private List<String> sourceRefs = new ArrayList<>();

    public static AgentAbilityGapFact of(Long abilityTagId, String abilityName,
                                         Integer currentLevel, Integer targetLevel,
                                         boolean required, boolean core) {
        AgentAbilityGapFact fact = new AgentAbilityGapFact();
        fact.setAbilityTagId(abilityTagId);
        fact.setAbilityName(abilityName);
        fact.setCurrentLevel(currentLevel);
        fact.setTargetLevel(targetLevel);
        fact.setGapLevel(currentLevel == null ? targetLevel : targetLevel - currentLevel);
        fact.setRequired(required);
        fact.setCore(core);
        return fact;
    }
}
