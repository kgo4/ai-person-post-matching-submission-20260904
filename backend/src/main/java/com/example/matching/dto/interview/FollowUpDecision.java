package com.example.matching.dto.interview;

/**
 * 追问决策 DTO
 * <p>
 * 由 InterviewFollowUpPolicyService 输出，决定是否追问及追问参数。
 */
public record FollowUpDecision(
    /** 是否应该追问 */
    Boolean shouldFollowUp,
    /** 追问类型（FollowUpType 枚举名） */
    String followUpType,
    /** 追问目标维度 */
    String targetDimension,
    /** 终止原因（shouldFollowUp=false 时有值） */
    String terminationReason,
    /** 当前题已追问轮数 */
    Integer currentFollowUpCount,
    /** 最大追问轮数 */
    Integer maxFollowUpCount
) {

    /**
     * 创建"需要追问"的决策
     */
    public static FollowUpDecision followUp(String followUpType, String targetDimension,
                                             int currentCount, int maxCount) {
        return new FollowUpDecision(true, followUpType, targetDimension, null, currentCount, maxCount);
    }

    /**
     * 创建"不需要追问"的决策
     */
    public static FollowUpDecision skip(String reason, int currentCount, int maxCount) {
        return new FollowUpDecision(false, null, null, reason, currentCount, maxCount);
    }
}
