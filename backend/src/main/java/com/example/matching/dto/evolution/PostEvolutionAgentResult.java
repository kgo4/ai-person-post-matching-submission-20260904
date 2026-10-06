package com.example.matching.dto.evolution;

import com.example.matching.entity.evolution.PostEvolutionEvidence;
import com.example.matching.service.evolution.PostEvolutionSignalService.EvolutionSignal;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 岗位演化 Agent 结果
 *
 * @author system
 */
@Data
public class PostEvolutionAgentResult {

    /** 岗位ID */
    private Long postId;

    /** 岗位名称 */
    private String postName;

    /** 演化摘要 */
    private String summary;

    /** 生成的信号列表 */
    private List<EvolutionSignal> signals;

    /** 收集的证据列表 */
    private List<PostEvolutionEvidence> evidences;

    /** 变更建议列表 */
    private List<PostEvolutionChangeProposal> proposals;

    /** Harness 校验摘要 */
    private HarnessSummary harnessSummary;

    /** 仅用于展示本次 AI 与规则建议的生成构成，不参与能力审核或写入。 */
    private Map<String, Object> proposalGenerationSummary;

    /**
     * 变更建议
     */
    @Data
    public static class PostEvolutionChangeProposal {
        private String signalType;
        private String abilityName;
        private Long abilityTagId;
        private String changeType;
        private Integer oldLevel;
        private Integer newLevel;
        private java.math.BigDecimal oldWeight;
        private java.math.BigDecimal newWeight;
        private Integer oldIsCore;
        private Integer newIsCore;
        private String reason;
        private String evidenceText;
        private List<String> sourceRefs;
        private Double confidenceScore;
        private Double supportScore;
        private String riskLevel;
        private String harnessDecision;
    }

    /**
     * Harness 校验摘要
     */
    @Data
    public static class HarnessSummary {
        private int pass;
        private int review;
        private int block;
        private int total;
    }
}
