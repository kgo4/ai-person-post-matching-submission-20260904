package com.example.matching.dto.system;

import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 标签准入结果
 * <p>
 * 由 AbilityTagService.admitNewTag() 返回，告知调用方标签准入决策。
 *
 * @author system
 */
@Data
@Builder
public class TagAdmissionResult {

    /**
     * 准入决策类型
     */
    private AdmissionDecision decision;

    /**
     * 匹配到的或新创建的正式标签（FORMAL_TAG_CREATED 或 EXISTING_TAG_REUSED 时有值）
     */
    private AbilityTag formalTag;

    /**
     * 创建的候选标签（CANDIDATE_CREATED 时有值）
     */
    private AbilityTagCandidate candidateTag;

    /**
     * 候选标签ID（CANDIDATE_CREATED 时有值）
     */
    private Long candidateId;

    /**
     * 决策原因说明
     */
    private String reason;

    /**
     * 匹配到的相似标签列表（EXISTING_TAG_REUSED 时可能有多个）
     */
    private List<AbilityTag> similarTags;

    /**
     * 相似度分数（EXISTING_TAG_REUSED 时有值）
     */
    private Float similarityScore;

    /**
     * 调用方已完成的 Harness 检查结果
     */
    private String harnessDecision;

    /**
     * Harness 支持分数（0-100）
     */
    private java.math.BigDecimal harnessScore;

    /**
     * Harness 检查日志 ID（checkCode），用于已验证来源的溯源审计
     */
    private String harnessLogId;

    /**
     * 准入决策枚举
     */
    public enum AdmissionDecision {
        /**
         * 已有标签被复用（精确匹配、别名匹配、相似度匹配）
         */
        EXISTING_TAG_REUSED,

        /**
         * 新标签已自动创建并入库
         */
        FORMAL_TAG_CREATED,

        /**
         * 进入候选池等待审核
         */
        CANDIDATE_CREATED,

        /**
         * 直接拒绝（垃圾、噪声、无证据等）
         */
        REJECTED
    }

    /**
     * 是否成功（复用或创建了正式标签）
     */
    public boolean isSuccess() {
        return decision == AdmissionDecision.EXISTING_TAG_REUSED
                || decision == AdmissionDecision.FORMAL_TAG_CREATED;
    }

    /**
     * 获取最终可用的标签ID（成功时返回，否则返回null）
     */
    public Long getResolvedTagId() {
        if (formalTag != null) {
            return formalTag.getId();
        }
        return null;
    }

    /**
     * 创建 EXISTING_TAG_REUSED 结果
     */
    public static TagAdmissionResult reused(AbilityTag tag, String reason, List<AbilityTag> similarTags, Float similarityScore) {
        return TagAdmissionResult.builder()
                .decision(AdmissionDecision.EXISTING_TAG_REUSED)
                .formalTag(tag)
                .reason(reason)
                .similarTags(similarTags)
                .similarityScore(similarityScore)
                .build();
    }

    /**
     * 创建 FORMAL_TAG_CREATED 结果
     */
    public static TagAdmissionResult created(AbilityTag tag, String harnessDecision, java.math.BigDecimal harnessScore) {
        return TagAdmissionResult.builder()
                .decision(AdmissionDecision.FORMAL_TAG_CREATED)
                .formalTag(tag)
                .harnessDecision(harnessDecision)
                .harnessScore(harnessScore)
                .reason("Harness PASS，新标签已自动入库")
                .build();
    }

    /**
     * 创建 CANDIDATE_CREATED 结果
     */
    public static TagAdmissionResult candidate(AbilityTagCandidate candidate, String harnessDecision, java.math.BigDecimal harnessScore) {
        return TagAdmissionResult.builder()
                .decision(AdmissionDecision.CANDIDATE_CREATED)
                .candidateTag(candidate)
                .candidateId(candidate != null ? candidate.getId() : null)
                .harnessDecision(harnessDecision)
                .harnessScore(harnessScore)
                .reason("Harness REVIEW，新标签进入候选池等待审核")
                .build();
    }

    /**
     * 创建 REJECTED 结果
     */
    public static TagAdmissionResult rejected(String reason) {
        return TagAdmissionResult.builder()
                .decision(AdmissionDecision.REJECTED)
                .reason(reason)
                .build();
    }
}
