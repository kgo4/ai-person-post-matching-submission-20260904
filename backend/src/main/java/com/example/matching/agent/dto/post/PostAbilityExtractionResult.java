package com.example.matching.agent.dto.post;

import lombok.Data;

import java.util.List;

/**
 * 岗位能力提取结果DTO
 * <p>
 * 统一输出格式，所有岗位能力提取结果都使用该DTO。
 * 包含提取的岗位能力声明列表和元数据。
 *
 * @author system
 */
@Data
public class PostAbilityExtractionResult {

    /** 岗位ID */
    private Long postId;

    /** 来源类型 */
    private String sourceType;

    /** 来源引用ID */
    private Long sourceRefId;

    /** 已通过岗位原文证据校验的岗位能力声明，可写入岗位模型 */
    private List<PostAbilityClaim> claims;

    /** 无正式标签的岗位能力；岗位模型照常写入，提交后可独立进入标签候选池 */
    private List<PostAbilityClaim> deferredClaims;

    /** 未通过岗位原文证据门禁的能力项，不进入岗位模型或标签候选池 */
    private List<PostAbilityClaim> rejectedClaims;

    /** 正式 claim 数量 */
    private int formalCount;

    /** 候选/待审 claim 数量 */
    private int pendingCount;

    /** 拒绝 claim 数量 */
    private int rejectedCount;

    /** 提取摘要 */
    private String summary;

    /** 是否使用降级方案 */
    private boolean fallbackUsed;

    /** 原始模型输出 */
    private String rawModelOutput;

    /** 提取耗时（毫秒） */
    private Long durationMs;

    /** 分块提取失败的分块数（>0 表示部分分块进入 RETRY/REVIEW） */
    private int failedChunkCount;

    /**
     * 获取有效的声明列表
     *
     * @return 有效声明列表
     */
    public List<PostAbilityClaim> getValidClaims() {
        if (claims == null) {
            return List.of();
        }
        return claims.stream()
                .filter(PostAbilityClaim::isValid)
                .toList();
    }

    /**
     * 获取已匹配正式标签的声明列表
     *
     * @return 已匹配声明列表
     */
    public List<PostAbilityClaim> getMatchedClaims() {
        if (claims == null) {
            return List.of();
        }
        return claims.stream()
                .filter(PostAbilityClaim::hasMatchedTag)
                .toList();
    }

    /**
     * 获取需要审核的声明列表（有相似标签但无正式标签）
     *
     * @return 需要审核声明列表
     */
    public List<PostAbilityClaim> getReviewNeededClaims() {
        if (claims == null) {
            return List.of();
        }
        return claims.stream()
                .filter(claim -> !claim.hasMatchedTag() && claim.hasSimilarTag())
                .toList();
    }

    /**
     * 获取无标签匹配的声明列表
     *
     * @return 无标签匹配声明列表
     */
    public List<PostAbilityClaim> getUnmatchedClaims() {
        if (claims == null) {
            return List.of();
        }
        return claims.stream()
                .filter(claim -> !claim.hasMatchedTag() && !claim.hasSimilarTag())
                .toList();
    }

    /**
     * 获取核心能力声明列表
     *
     * @return 核心能力声明列表
     */
    public List<PostAbilityClaim> getCoreClaims() {
        if (claims == null) {
            return List.of();
        }
        return claims.stream()
                .filter(PostAbilityClaim::isCoreAbility)
                .toList();
    }

    /**
     * 获取必填能力声明列表
     *
     * @return 必填能力声明列表
     */
    public List<PostAbilityClaim> getRequiredClaims() {
        if (claims == null) {
            return List.of();
        }
        return claims.stream()
                .filter(PostAbilityClaim::isRequiredAbility)
                .toList();
    }

    /**
     * 获取声明总数
     *
     * @return 声明总数
     */
    public int getClaimCount() {
        return claims != null ? claims.size() : 0;
    }

    /**
     * 获取有效声明总数
     *
     * @return 有效声明总数
     */
    public int getValidClaimCount() {
        return getValidClaims().size();
    }

    /**
     * 获取核心能力总数
     *
     * @return 核心能力总数
     */
    public int getCoreClaimCount() {
        return getCoreClaims().size();
    }
}
