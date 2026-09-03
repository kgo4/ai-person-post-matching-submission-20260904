package com.example.matching.agent.dto.graph;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 预计算能力满足关系。
 * <p>
 * matchState 取值：SATISFIED / LEVEL_GAP / MISSING / BONUS
 */
@Data
public class AgentAbilityMatchFact {

    /** 能力标签 ID */
    private Long abilityTagId;

    /** 能力名称 */
    private String abilityName;

    /** 员工当前等级（权威源：融合画像优先，回退 emp_ability） */
    private Integer employeeLevel;

    /** 岗位要求等级 */
    private Integer requiredLevel;

    /** 岗位权重 */
    private BigDecimal weight;

    /** 是否必填 */
    private boolean required;

    /** 是否核心 */
    private boolean core;

    /** 匹配状态：SATISFIED / LEVEL_GAP / MISSING / BONUS */
    private String matchState;

    /** 支撑该事实的来源引用 */
    private List<String> sourceRefs = new ArrayList<>();

    public static AgentAbilityMatchFact of(Long abilityTagId, String abilityName,
                                           Integer employeeLevel, Integer requiredLevel,
                                           BigDecimal weight, boolean required, boolean core,
                                           String matchState) {
        AgentAbilityMatchFact fact = new AgentAbilityMatchFact();
        fact.setAbilityTagId(abilityTagId);
        fact.setAbilityName(abilityName);
        fact.setEmployeeLevel(employeeLevel);
        fact.setRequiredLevel(requiredLevel);
        fact.setWeight(weight);
        fact.setRequired(required);
        fact.setCore(core);
        fact.setMatchState(matchState);
        return fact;
    }
}
