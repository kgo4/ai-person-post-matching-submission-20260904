package com.example.matching.agent.dto.interview;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * AI 测试题目项（扩展后的题目契约，见实施计划 §1.2）。
 * <p>
 * 每道核验题必须绑定评估范围（AssessmentScope）内的能力标签、岗位要求与证据来源：
 * <ul>
 *   <li>{@code abilityTagId}：只能取 scope.items 中的 abilityTagId；</li>
 *   <li>{@code postRequirementId}：只能取 scope.items 中与 abilityTagId 对应的岗位要求；</li>
 *   <li>{@code sourceClaimIds}：来源简历 Claim ID（scope 内）；</li>
 *   <li>{@code sourceEvidenceRefs}：来源证据引用；</li>
 *   <li>{@code verificationType}：CLAIM_RECALL / POST_SCENARIO / LEVEL_DISCRIMINATION；</li>
 *   <li>{@code scoringRubric}：评分 rubric（answer/referenceAnswer 不能作为唯一评分依据）。</li>
 * </ul>
 * 旧字段 {@code tagId}/{@code sourceRefs} 保留用于向后兼容。
 */
@Data
@NoArgsConstructor
public class AiTestQuestionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String questionId;
    private String question;
    private String type;
    private String difficulty;
    private List<String> options;
    private String referenceAnswer;
    private Integer score;

    /** 旧字段：能力标签 ID（兼容） */
    private Long tagId;
    /** 旧字段：来源引用（兼容） */
    private List<String> sourceRefs;

    /** 核验能力标签 ID（必须 ∈ scope.items.abilityTagId） */
    private Long abilityTagId;
    /** 多能力核验题绑定的既有简历标签；与 abilityTagId 兼容，至少存在一个字段。 */
    private List<Long> abilityTagIds;
    /** 岗位能力要求 ID（必须与 abilityTagId 对应） */
    private Long postRequirementId;
    /** 来源简历 Claim ID（scope 内） */
    private List<Long> sourceClaimIds;
    /** 聚合题中每项能力与其原始简历声明的精确绑定关系 */
    private List<VerificationBinding> verificationBindings;
    /** 来源证据引用 */
    private List<String> sourceEvidenceRefs;
    /** 核验类型：CLAIM_RECALL / POST_SCENARIO / LEVEL_DISCRIMINATION */
    private String verificationType;
    /** 目标等级 */
    private Integer targetLevel;
    /** 评分 rubric */
    private String scoringRubric;

    @Data
    @NoArgsConstructor
    public static class VerificationBinding implements Serializable {
        private Long abilityTagId;
        private List<Long> sourceClaimIds;
    }

    /** 兼容旧 8 参数构造器（question, type, difficulty, options, referenceAnswer, score, tagId, sourceRefs） */
    public AiTestQuestionItem(String question, String type, String difficulty,
                              List<String> options, String referenceAnswer,
                              Integer score, Long tagId, List<String> sourceRefs) {
        this.question = question;
        this.type = type;
        this.difficulty = difficulty;
        this.options = options;
        this.referenceAnswer = referenceAnswer;
        this.score = score;
        this.tagId = tagId;
        this.sourceRefs = sourceRefs;
    }
}
