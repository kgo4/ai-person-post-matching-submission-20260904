package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI能力抽取结果DTO（双轨输出）
 * <p>
 * AI生成时不是只能用旧标签，而是分两类输出：
 * 1. mappedAbilities: 匹配到正式标签库的能力
 * 2. candidateAbilities: 未匹配到正式标签的新能力（进入候选池）
 */
@Data
@Schema(description = "AI能力抽取结果，包含已匹配的正式能力和待审核的候选能力")
public class AbilityExtractResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 已匹配到正式标签库的能力 */
    @Schema(description = "已匹配到正式标签库的能力列表")
    private List<MappedAbility> mappedAbilities;

    /** 未匹配到正式标签的新能力（进入候选池） */
    @Schema(description = "未匹配到正式标签的新能力列表，将进入候选标签池")
    private List<CandidateAbility> candidateAbilities;

    /**
     * 已匹配到正式标签的能力
     */
    @Data
    @Schema(description = "已匹配到正式标签的能力项")
    public static class MappedAbility implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "正式标签ID")
        private Long tagId;

        @Schema(description = "正式标签名称")
        private String tagName;

        @Schema(description = "最低要求等级：1-5")
        private Integer minRequiredLevel;

        @Schema(description = "建议权重")
        private BigDecimal weight;

        @Schema(description = "是否核心项")
        private Integer isCore;

        @Schema(description = "是否必填")
        private Integer isRequired;

        @Schema(description = "匹配来源：exact-精确匹配, similar-相似匹配, alias-别名匹配")
        private String matchSource;

        @Schema(description = "AI推理依据")
        private String reasoning;
    }

    /**
     * 未匹配到正式标签的新能力（进入候选池）
     */
    @Data
    @Schema(description = "未匹配到正式标签的新能力项，将进入候选标签池审核")
    public static class CandidateAbility implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "候选能力名称")
        private String candidateName;

        @Schema(description = "AI推荐理由")
        private String reason;

        @Schema(description = "证据片段")
        private String evidenceText;

        @Schema(description = "建议分类：TECHNICAL, SOFT, BUSINESS")
        private String suggestedCategory;

        @Schema(description = "建议领域")
        private String suggestedDomain;

        @Schema(description = "在JD/简历中出现的上下文")
        private String contextText;
    }

    /**
     * 是否有已匹配的能力
     */
    public boolean hasMappedAbilities() {
        return mappedAbilities != null && !mappedAbilities.isEmpty();
    }

    /**
     * 是否有候选能力
     */
    public boolean hasCandidateAbilities() {
        return candidateAbilities != null && !candidateAbilities.isEmpty();
    }

    /**
     * 获取已匹配能力数量
     */
    public int getMappedCount() {
        return mappedAbilities == null ? 0 : mappedAbilities.size();
    }

    /**
     * 获取候选能力数量
     */
    public int getCandidateCount() {
        return candidateAbilities == null ? 0 : candidateAbilities.size();
    }
}
