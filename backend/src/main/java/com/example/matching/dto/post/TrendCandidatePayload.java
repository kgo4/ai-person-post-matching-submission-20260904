package com.example.matching.dto.post;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 趋势候选的详情载荷，序列化后存入 {@code post_trend_candidate.candidate_payload}。
 * <p>
 * 卡片摘要只用岗位名 / 相似度 / 强调度 / 前几个能力名；其余全部收在这里，由抽屉按需展示，
 * 避免首屏出现「巨量信息」。
 */
@Data
public class TrendCandidatePayload {

    private List<String> responsibilities = new ArrayList<>();

    private List<String> businessScenarios = new ArrayList<>();

    /** LLM 自评的材料强调度 0-10 */
    private BigDecimal llmEmphasisScore;

    private String emphasisReason;

    /** 与「材料内实际提及次数」交叉校验后的最终强调度；LLM 拔高但材料未提及时会被压低 */
    private BigDecimal effectiveEmphasisScore;

    /** 材料中提到该岗位的片段数 */
    private Integer mentionCount;

    /** 提及该岗位的材料类别数，≥2 表示多来源印证 */
    private Integer sourceCoverage;

    /** Harness 治理判定的理由，供审核人判断「为什么被判待复核 / 被拦截」 */
    private String harnessReason;

    /**
     * 既有岗位已有、但材料未提及的能力。
     * <p>
     * <b>仅供人工参考，不构成删除建议</b>：材料不完整是常态，「材料没提」不等于「岗位不需要」。
     * 若据此自动删除能力，一次材料缺章就会砍掉真实岗位要求。
     */
    private List<String> unmatchedExistingAbilities = new ArrayList<>();

    private List<TrendAbilityItem> abilities = new ArrayList<>();

    @Data
    public static class TrendAbilityItem {

        private String abilityName;

        /** 归位成功的正式标签ID；为空表示未归位（与 V131「岗位能力画像与标签解耦」口径一致） */
        private Long tagId;

        private String matchedTagName;

        /** 是否已归位到系统既有能力标签 */
        private boolean resolved;

        /** 未归位时最相似的既有标签，供人工判断「采用现有标签 / 确认新增」 */
        private Long similarTagId;

        private String similarTagName;

        private BigDecimal similarity;

        /** 未归位时写入的能力标签候选ID（ability_tag_candidate） */
        private Long tagCandidateId;

        private Integer suggestedLevel;

        private BigDecimal suggestedWeight;

        private Integer isCore;

        /** 支持该能力的材料片段编号 */
        private Integer evidenceRef;

        /** 由 evidenceRef 解析出的标准来源引用，供 Harness 校验真实性 */
        private String sourceRef;

        /** ADD / UPGRADE_LEVEL / DOWNGRADE_LEVEL / UPDATE_WEIGHT / UNCHANGED（新岗位候选恒为 ADD） */
        private String changeType;

        private Integer existingLevel;

        private BigDecimal existingWeight;
    }
}
