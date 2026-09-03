package com.example.matching.agent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 员工能力Agent结果DTO
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmployeeAbilityAgentResult extends AgentRunResult {
    /** 能力画像总结 */
    private String summary;

    /** 优势能力列表 */
    private List<AbilityItem> strongAbilities;

    /** 薄弱能力列表 */
    private List<AbilityItem> weakAbilities;

    /** 缺失证据列表 */
    private List<MissingEvidenceItem> missingEvidence;

    /** 风险信号列表 */
    private List<String> riskSignals;

    /** 补充建议列表 */
    private List<String> suggestions;

    /**
     * 能力项
     */
    @Data
    public static class AbilityItem {
        private Long abilityTagId;
        private String abilityName;
        private Integer level;
        private String source;
        private Integer credibility;
    }

    /**
     * 缺失证据项
     */
    @Data
    public static class MissingEvidenceItem {
        private Long abilityTagId;
        private String abilityName;
        private String reason;
        private String suggestion;
    }
}
