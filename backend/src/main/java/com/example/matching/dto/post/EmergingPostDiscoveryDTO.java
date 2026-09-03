package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 新兴岗位发现结果DTO
 */
@Data
@Schema(description = "新兴岗位发现结果")
public class EmergingPostDiscoveryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "候选岗位名称")
    private String candidateName;

    @Schema(description = "岗位描述")
    private String description;

    @Schema(description = "核心能力组合")
    private List<String> coreAbilities;

    @Schema(description = "出现频率（基于JD文档数量）")
    private Integer frequency;

    @Schema(description = "新颖度评分 0-100（越高表示越新颖）")
    private Integer noveltyScore;

    @Schema(description = "市场热度评分 0-100")
    private Integer marketHeatScore;

    @Schema(description = "相关行业领域")
    private List<String> relatedIndustries;

    @Schema(description = "发现来源摘要")
    private String sourceSummary;

    @Schema(description = "新兴岗位评分 0-100（综合评分）")
    private Integer emergenceScore;

    @Schema(description = "趋势增长评分 0-100")
    private Integer trendGrowthScore;

    @Schema(description = "来源多样性评分 0-100")
    private Integer sourceDiversityScore;

    @Schema(description = "候选证据覆盖的平台数量")
    private Integer sourcePlatformCount;

    @Schema(description = "候选证据覆盖的独立招聘主体数量，仅返回聚合数量")
    private Integer independentEmployerCount;

    @Schema(description = "独立招聘主体多样性评分 0-100")
    private Integer companyDiversityScore;

    @Schema(description = "语义新颖度评分 0-100")
    private Integer semanticNoveltyScore;

    @Schema(description = "内部需求评分 0-100")
    private Integer internalDemandScore;

    @Schema(description = "证据可信度评分 0-100")
    private Integer evidenceCredibilityScore;

    @Schema(description = "来源引用列表（统一sourceRef格式）")
    private List<String> sourceRefs;

    @Schema(description = "Harness 决策：PASS/REVIEW/BLOCK")
    private String harnessDecision;

    @Schema(description = "审核状态：PENDING/APPROVED/REJECTED")
    private String reviewStatus;

    @Schema(description = "关联的既有岗位ID列表")
    private List<Long> relatedExistingPostIds;

    @Schema(description = "差异化原因")
    private String differentiationReason;

    @Schema(description = "定义说明")
    private String definition;

    @Schema(description = "业务场景")
    private String businessScenario;

    @Schema(description = "核心任务")
    private List<String> coreTasks;

    @Schema(description = "发现模式：OBSERVATION/CANDIDATE/DISCOVERY")
    private String discoveryMode;

    @Schema(description = "技能社区凝聚度评分 0-100")
    private Integer cohesionScore;

    @Schema(description = "建议流转：POST_EVOLUTION/EMERGING_POST_REVIEW")
    private String recommendedAction;

    /**
     * 市场洞察数据
     */
    @Data
    public static class MarketInsight implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "热门能力标签TOP10")
        private List<HotAbility> hotAbilities;

        @Schema(description = "新兴技术趋势")
        private List<TechTrend> techTrends;

        @Schema(description = "数据更新时间")
        private String lastUpdated;

        @Schema(description = "分析的JD文档数量")
        private Integer analyzedJdCount;

        @Schema(description = "当前已形成的候选岗位方向总数")
        private Integer candidateCount;

        @Schema(description = "有效 JD 覆盖的来源平台数量")
        private Integer sourcePlatformCount;

        @Schema(description = "有效 JD 覆盖的独立招聘主体数量，仅返回聚合数量")
        private Integer independentEmployerCount;

        @Schema(description = "来源平台多样性评分 0-100")
        private Integer sourceDiversityScore;

        @Schema(description = "独立招聘主体多样性评分 0-100")
        private Integer companyDiversityScore;

        @Schema(description = "精确或近似去重后被跳过的 JD 数量")
        private Integer deduplicatedCount;

        @Schema(description = "按市场噪声规则过滤的 JD 数量")
        private Integer noiseFilteredCount;
    }

    /**
     * 热门能力标签
     */
    @Data
    public static class HotAbility implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "能力名称")
        private String abilityName;

        @Schema(description = "提及次数")
        private Integer mentionCount;

        @Schema(description = "增长率（相比上期）")
        private Integer growthRate;

        @Schema(description = "相关岗位数量")
        private Integer relatedPostCount;
    }

    /**
     * 技术趋势
     */
    @Data
    public static class TechTrend implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "技术名称")
        private String techName;

        @Schema(description = "趋势方向：RISING/STABLE/DECLINING")
        private String trendDirection;

        @Schema(description = "热度评分 0-100")
        private Integer heatScore;

        @Schema(description = "典型应用场景")
        private String typicalScenario;
    }
}
