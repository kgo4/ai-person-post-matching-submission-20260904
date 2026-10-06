package com.example.matching.dto.harness;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AI Harness 决策 DTO
 * <p>
 * Harness 校验后的决策结果，包含决策、风险等级、支持分数等信息。
 *
 * @author system
 */
@Data
public class AiHarnessDecisionDTO {

    public static final String PASS = "PASS";
    public static final String REVIEW = "REVIEW";
    public static final String BLOCK = "BLOCK";
    public static final String RETRY = "RETRY";

    /** 检查编码：唯一标识 */
    private String checkCode;

    /** Stable aggregate-assessment item identifier returned by batch verification. */
    private Long claimGroupId;

    /** 决策：PASS, REVIEW, BLOCK */
    private String decision;

    /** 风险等级：LOW, MEDIUM, HIGH */
    private String riskLevel;

    /** 支持分数：0-100 */
    private BigDecimal supportScore;

    /** 是否AI自证 */
    private boolean selfEvidence;

    /** 匹配到的正式标签ID */
    private Long matchedTagId;

    /** 相似标签ID */
    private Long similarTagId;

    /** 决策原因列表 */
    private List<String> reasons = new ArrayList<>();

    /** 接受的来源引用列表 */
    private List<String> acceptedSourceRefs = new ArrayList<>();

    /** 无效的来源引用列表 */
    private List<String> invalidSourceRefs = new ArrayList<>();

    /** 缺失的证据列表 */
    private List<String> missingEvidence = new ArrayList<>();

    /** 无法验证的引用列表（resolve 异常或服务不可用） */
    private List<String> unverifiableSourceRefs = new ArrayList<>();

    /** 原支持分数（Harness 校验前的原始分数） */
    private BigDecimal legacySupportScore;

    /** 原决策（legacy 幻觉检查的决策） */
    private String legacyDecision;

    /** 决策合并规则描述 */
    private String decisionRule;

    /** 追踪ID */
    private String traceId;

    /**
     * 是否通过
     *
     * @return 是否通过
     */
    public boolean isPass() {
        return PASS.equals(decision);
    }

    /**
     * 是否需要审核
     *
     * @return 是否需要审核
     */
    public boolean isReview() {
        return REVIEW.equals(decision);
    }

    /**
     * 是否被阻止
     *
     * @return 是否被阻止
     */
    public boolean isBlock() {
        return BLOCK.equals(decision);
    }

    public boolean isRetry() {
        return RETRY.equals(decision);
    }

    /**
     * 是否有接受的来源引用
     *
     * @return 是否有接受的来源引用
     */
    public boolean hasAcceptedSourceRefs() {
        return acceptedSourceRefs != null && !acceptedSourceRefs.isEmpty();
    }

    /**
     * 是否有无效的来源引用
     *
     * @return 是否有无效的来源引用
     */
    public boolean hasInvalidSourceRefs() {
        return invalidSourceRefs != null && !invalidSourceRefs.isEmpty();
    }

    /**
     * 是否有缺失的证据
     *
     * @return 是否有缺失的证据
     */
    public boolean hasMissingEvidence() {
        return missingEvidence != null && !missingEvidence.isEmpty();
    }
}
