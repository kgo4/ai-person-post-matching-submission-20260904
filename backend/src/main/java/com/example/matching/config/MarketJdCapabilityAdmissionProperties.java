package com.example.matching.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 市场 JD 能力自动准入（Market JD Capability Auto-Admission）类型化配置
 * <p>
 * 前缀：{@code market-jd.capability-admission}。
 * 默认值即生产初始值；通过配置（而非代码）调整。
 */
@Data
@Component
@ConfigurationProperties(prefix = "market-jd.capability-admission")
public class MarketJdCapabilityAdmissionProperties {

    /** 特性总开关：false 时保持现有行为不变（feature-flag 部署用） */
    private boolean enabled = true;

    /** 直接证据（规范名/别名命中）自动准入开关 */
    private boolean directEvidenceAutoAdmit = true;

    /** High-confidence semantic matches are recommendations, not formal admissions. */
    private double semanticRecommendationMinScore = 0.88;

    /** AiTrustHarnessService.verifyBatch 每批最大 claim 数 */
    private int harnessBatchSize = 50;

    /** 单条 claim 的 RETRY 重试次数（仅允许 0 或 1） */
    private int harnessRetryCount = 1;

    /** 新能力进入 Harness 分组所需的最少 JD 数 */
    private int newAbilityMinJdCount = 3;

    /** 新能力进入 Harness 分组所需的最少公司数（companyDiversityKey 去重） */
    private int newAbilityMinCompanyCount = 2;

    /** 新能力 PASS 自动建/复用正式标签所需的最低支持分（0-100） */
    private int newAbilityPassMinScore = 80;

    /** 单批 REVIEW 新能力分组数上限（0 表示全部抑制为拒绝，不自动通过） */
    private int reviewMaxGroupsPerBatch = 20;

    @PostConstruct
    public void validate() {
        if (harnessBatchSize < 1) {
            throw new IllegalStateException(
                    "market-jd.capability-admission.harness-batch-size 必须 >= 1，当前: " + harnessBatchSize);
        }
        if (semanticRecommendationMinScore < 0 || semanticRecommendationMinScore > 1) {
            throw new IllegalStateException("semantic-recommendation-min-score must be between 0 and 1");
        }
        if (harnessRetryCount != 0 && harnessRetryCount != 1) {
            throw new IllegalStateException(
                    "market-jd.capability-admission.harness-retry-count 必须为 0 或 1，当前: " + harnessRetryCount);
        }
        if (newAbilityMinJdCount < 1) {
            throw new IllegalStateException(
                    "market-jd.capability-admission.new-ability-min-jd-count 必须 >= 1，当前: " + newAbilityMinJdCount);
        }
        if (newAbilityMinCompanyCount < 1) {
            throw new IllegalStateException(
                    "market-jd.capability-admission.new-ability-min-company-count 必须 >= 1，当前: " + newAbilityMinCompanyCount);
        }
        if (newAbilityPassMinScore < 0 || newAbilityPassMinScore > 100) {
            throw new IllegalStateException(
                    "market-jd.capability-admission.new-ability-pass-min-score 必须在 0-100，当前: " + newAbilityPassMinScore);
        }
        if (reviewMaxGroupsPerBatch < 0) {
            throw new IllegalStateException(
                    "market-jd.capability-admission.review-max-groups-per-batch 必须 >= 0，当前: " + reviewMaxGroupsPerBatch);
        }
    }
}
