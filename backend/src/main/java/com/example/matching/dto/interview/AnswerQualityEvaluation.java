package com.example.matching.dto.interview;

import java.util.List;

/**
 * 回答质量评估 DTO
 * <p>
 * 由 InterviewAnswerQualityService 输出，供 InterviewFollowUpPolicyService 消费。
 */
public record AnswerQualityEvaluation(
    /** STAR 完整性评估 */
    StarCompleteness starCompleteness,
    /** 具体性评分 0-100 */
    Integer specificityScore,
    /** 事实证据评分 0-100 */
    Integer evidenceScore,
    /** 个人贡献评分 0-100 */
    Integer personalContributionScore,
    /** 逻辑一致性评分 0-100 */
    Integer logicConsistencyScore,
    /** 是否需要追问 */
    Boolean needFollowUp,
    /** 追问原因 */
    String followUpReason,
    /** 追问目标维度 */
    String targetDimension,
    /** 建议追问类型（FollowUpType 枚举名） */
    String suggestedFollowUpType,
    /** 缺失证据列表 */
    List<String> missingEvidence,
    /** 逻辑风险列表 */
    List<String> logicRisks,
    /** 评估结论 */
    String conclusion,
    /** 服务端根据会话和题目生成的可追溯事实引用 */
    List<String> sourceRefs
) {

    public AnswerQualityEvaluation(StarCompleteness starCompleteness,
                                   Integer specificityScore,
                                   Integer evidenceScore,
                                   Integer personalContributionScore,
                                   Integer logicConsistencyScore,
                                   Boolean needFollowUp,
                                   String followUpReason,
                                   String targetDimension,
                                   String suggestedFollowUpType,
                                   List<String> missingEvidence,
                                   List<String> logicRisks,
                                   String conclusion) {
        this(starCompleteness, specificityScore, evidenceScore, personalContributionScore,
                logicConsistencyScore, needFollowUp, followUpReason, targetDimension,
                suggestedFollowUpType, missingEvidence, logicRisks, conclusion, List.of());
    }

    /**
     * STAR 完整性评估
     */
    public record StarCompleteness(
        /** 是否有背景描述 */
        Boolean situation,
        /** 是否有任务描述 */
        Boolean task,
        /** 是否有行动描述 */
        Boolean action,
        /** 是否有结果描述 */
        Boolean result
    ) {}

    /**
     * 计算综合评分
     */
    public int overallScore() {
        int sum = 0;
        int count = 0;
        if (specificityScore != null) { sum += specificityScore; count++; }
        if (evidenceScore != null) { sum += evidenceScore; count++; }
        if (personalContributionScore != null) { sum += personalContributionScore; count++; }
        if (logicConsistencyScore != null) { sum += logicConsistencyScore; count++; }
        return count > 0 ? sum / count : 0;
    }
}
